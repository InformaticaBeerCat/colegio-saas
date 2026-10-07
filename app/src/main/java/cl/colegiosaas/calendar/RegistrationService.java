package cl.colegiosaas.calendar;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.platform.Formats;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.ConsentService;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.security.SecureTokens;
import cl.colegiosaas.shared.text.Emails;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.Course;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Inscripción a eventos con cupos, lista de espera y cierre automático (EVE-01, EVE-02). El cupo se decide con la
 * fila del evento bloqueada; al liberarse lugares sube la lista de espera en orden de llegada. Cada inscripción
 * trae un enlace secreto para cancelarla sin cuenta.
 */
@Service
public class RegistrationService {

    /** Personas por inscripción: una familia, no un curso entero. */
    public static final int MAX_ATTENDEES = 10;
    private static final EnumSet<RegistrationStatus> ACTIVE = EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED);

    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final ConsentService consents;
    private final SchoolRepository schools;
    private final SchoolTime time;
    private final Formats formats;
    private final EventDates dates;
    private final AppProperties app;
    private final BlindIndex index;
    private final AuditTrail audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    RegistrationService(EventRepository events, EventRegistrationRepository registrations, ConsentService consents,
                        SchoolRepository schools, SchoolTime time, Formats formats, EventDates dates, AppProperties app,
                        BlindIndex index, AuditTrail audit, ApplicationEventPublisher publisher, Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.consents = consents;
        this.schools = schools;
        this.time = time;
        this.formats = formats;
        this.dates = dates;
        this.app = app;
        this.index = index;
        this.audit = audit;
        this.publisher = publisher;
        this.clock = clock;
    }

    // --- Lo que ve el sitio --------------------------------------------------------------------------------

    /**
     * Estado de la inscripción de un evento para el sitio.
     *
     * @param seatsLeft cupos que quedan; nulo si no hay límite
     */
    public record Offer(boolean open, Integer seatsLeft, boolean waitlist, List<Course> courses) {

        public boolean full() {
            return seatsLeft != null && seatsLeft == 0;
        }

        /** Se puede enviar el formulario: hay cupo o hay lista de espera. */
        public boolean accepting() {
            return open && (!full() || waitlist);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Offer> offer(Event event) {
        if (!event.isRegistrationEnabled()) {
            return Optional.empty();
        }
        OptionalInt left = event.seatsLeft(registrations.countConfirmedAttendees(event));
        List<Course> courses = event.getCourses().stream()
                .sorted(Comparator.comparing((Course c) -> c.getGradeLevel().getSortOrder()).thenComparing(Course::getSection))
                .toList();
        return Optional.of(new Offer(event.acceptsRegistrationsAt(time.now()), left.isPresent() ? left.getAsInt() : null,
                event.isWaitlistEnabled(), courses));
    }

    /** Lo que llega del formulario del sitio. */
    public record Submission(String name, String email, String phone, int attendees, Long courseId, String studentName,
                             boolean consent) {
    }

    /** Resultado para la persona: confirmada o en lista de espera, y el enlace para cancelar. */
    public record Result(EventRegistration registration, String manageUrl) {
    }

    @Transactional
    public Result register(long eventId, Submission form, RequestOrigin origin) {
        Event event = events.findForUpdate(eventId).orElseThrow(() -> new NotFound("El evento no existe"));
        if (!event.acceptsRegistrationsAt(time.now())) {
            throw new RuleViolation("La inscripción para este evento está cerrada");
        }
        String name = required(form.name(), 150, "Escribe tu nombre (hasta 150 caracteres)");
        String email = form.email() == null ? "" : form.email().strip();
        if (!Emails.isValid(email)) {
            throw new RuleViolation("Escribe un correo válido: ahí te llega la confirmación");
        }
        if (form.attendees() < 1 || form.attendees() > MAX_ATTENDEES) {
            throw new RuleViolation("Pueden inscribirse entre 1 y " + MAX_ATTENDEES + " personas por formulario");
        }
        Course course = null;
        if (!event.getCourses().isEmpty()) {
            course = event.getCourses().stream().filter(c -> c.getId().equals(form.courseId())).findFirst()
                    .orElseThrow(() -> new RuleViolation("Elige el curso"));
        }
        if (!form.consent()) {
            throw new RuleViolation("Para inscribirte necesitamos tu autorización para usar estos datos: marca la casilla");
        }
        if (registrations.existsByEventAndEmailHashAndStatusIn(event, index.of(email), ACTIVE)) {
            throw new RuleViolation("Ya hay una inscripción con ese correo. Revisa el correo de confirmación para cambiarla o cancelarla.");
        }
        DataSubject person = new DataSubject(name, email);
        var registrant = new EventRegistration.Registrant(person, optional(form.phone(), 30), form.attendees(), course,
                optional(form.studentName(), 150));

        OptionalInt left = event.seatsLeft(registrations.countConfirmedAttendees(event));
        boolean fits = left.isEmpty() || left.getAsInt() >= form.attendees();
        if (!fits && !event.isWaitlistEnabled()) {
            throw new RuleViolation(left.getAsInt() == 0 ? "Se agotaron los cupos"
                    : "Quedan " + left.getAsInt() + " cupo(s): inscribe a menos personas");
        }
        ConsentRecord consent = consents.record(person, ConsentPurpose.EVENT_REGISTRATION, true, origin);
        EventRegistration.Created created = fits
                ? EventRegistration.confirmed(event, registrant, consent, index)
                : EventRegistration.waitlisted(event, registrant, nextWaitlistPosition(event), consent, index);
        EventRegistration registration = registrations.save(created.registration());
        audit.recordAnonymous(AuditAction.CREATE, "EventRegistration", registration.getId(), event.getTitle() + ": " + registration.getStatus());

        String manageUrl = app.url("/inscripciones/" + created.manageToken());
        publisher.publishEvent(new OutgoingMail(email, (fits ? "Inscripción confirmada: " : "Lista de espera: ") + event.getTitle(), """
                Hola %s:

                %s

                Evento: %s
                Cuándo: %s%s
                Personas: %d

                Para cancelar tu inscripción (y dejar el lugar a otra familia): %s
                Para agregarlo a tu calendario: %s

                %s
                """.formatted(name,
                fits ? "Tu inscripción quedó confirmada."
                        : "El evento está lleno: quedaste en el lugar " + registration.getWaitlistPosition()
                        + " de la lista de espera. Si se libera un cupo te avisaremos por correo.",
                event.getTitle(), dates.of(event), event.getLocation() == null ? "" : "\nDónde: " + event.getLocation(),
                form.attendees(), manageUrl, app.url("/calendario/" + event.getSlug() + ".ics"), schoolName())));
        return new Result(registration, manageUrl);
    }

    // --- Enlace de la persona -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public EventRegistration byToken(String token) {
        return registrations.findWithEventByManageTokenHash(SecureTokens.hash(token == null ? "" : token))
                .orElseThrow(() -> new NotFound("El enlace no es válido"));
    }

    /** La persona cancela desde su enlace: el lugar pasa a la lista de espera. */
    @Transactional
    public void cancelByToken(String token) {
        EventRegistration registration = byToken(token);
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            return;
        }
        if (registration.getEvent().getStartsAt().isBefore(time.now())) {
            throw new RuleViolation("El evento ya comenzó");
        }
        events.findForUpdate(registration.getEvent().getId());
        cancel(registration);
        audit.recordAnonymous(AuditAction.UPDATE, "EventRegistration", registration.getId(), "Cancelada por la persona");
    }

    // --- Panel -----------------------------------------------------------------------------------------------

    @Transactional
    public void configure(long eventId, boolean enabled, Integer capacity, boolean waitlist, LocalDateTime closesAt) {
        Event event = events.findForUpdate(eventId).orElseThrow(() -> new NotFound("El evento no existe"));
        if (!enabled) {
            event.closeRegistration();
            audit.record(AuditAction.UPDATE, "Event", eventId, "Inscripción cerrada");
            return;
        }
        if (capacity != null && capacity < 1) {
            throw new RuleViolation("El aforo debe ser al menos 1 persona (o déjalo vacío si no hay límite)");
        }
        if (closesAt != null && closesAt.isAfter(event.getStartsAt())) {
            throw new RuleViolation("La inscripción debe cerrar antes de que empiece el evento");
        }
        boolean hadWaitlist = registrations.countByEventAndStatus(event, RegistrationStatus.WAITLISTED) > 0;
        if (hadWaitlist && !(waitlist && capacity != null)) {
            throw new RuleViolation("Hay personas en lista de espera: primero libera cupos o cancela sus inscripciones");
        }
        event.openRegistration(capacity, waitlist, closesAt);
        audit.record(AuditAction.UPDATE, "Event", eventId, "Inscripción: aforo " + (capacity == null ? "sin límite" : capacity));
        promoteWaitlist(event);
    }

    /** Inscritos del evento: el panel ve datos de personas y queda registrado. */
    @Transactional
    public List<EventRegistration> roster(long eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFound("El evento no existe"));
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "Event", eventId, "Lista de inscritos");
        return registrations.findWithCourseByEventOrderByCreatedAtAsc(event);
    }

    @Transactional
    public void cancelFromPanel(long registrationId) {
        EventRegistration registration = registrations.findById(registrationId)
                .orElseThrow(() -> new NotFound("La inscripción no existe"));
        events.findForUpdate(registration.getEvent().getId());
        cancel(registration);
        audit.record(AuditAction.UPDATE, "EventRegistration", registrationId, "Cancelada desde el panel");
    }

    @Transactional
    public void markAttended(long registrationId) {
        EventRegistration registration = registrations.findById(registrationId)
                .orElseThrow(() -> new NotFound("La inscripción no existe"));
        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new RuleViolation("Solo se marca asistencia de inscripciones confirmadas");
        }
        registration.markAttended();
        audit.record(AuditAction.UPDATE, "EventRegistration", registrationId, "Asistió");
    }

    /** Planilla de asistencia (CSV, separado por punto y coma para Excel en español). */
    @Transactional
    public String rosterCsv(long eventId) {
        StringBuilder csv = new StringBuilder("Estado;Lugar en espera;Nombre;Correo;Teléfono;Personas;Curso;Estudiante;Inscrito\n");
        for (EventRegistration r : roster(eventId)) {
            csv.append(String.join(";", r.getStatus().name(),
                    r.getWaitlistPosition() == null ? "" : String.valueOf(r.getWaitlistPosition()),
                    cell(r.getName()), cell(r.getEmail()), cell(r.getPhone()), String.valueOf(r.getAttendees()),
                    r.getCourse() == null ? "" : cell(r.getCourse().displayName()), cell(r.getStudentName()),
                    formats.dateTime(r.getCreatedAt()))).append('\n');
        }
        audit.record(AuditAction.EXPORT, "Event", eventId, "Planilla de inscritos");
        return csv.toString();
    }

    // --- Reuniones de apoderados (AGE-08) ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Event> upcomingParentMeetings() {
        return events.findPublishedOfKindFrom(EventKind.PARENT_MEETING, time.now().toLocalDate().atStartOfDay());
    }

    // --- Apoyo -----------------------------------------------------------------------------------------------

    private void cancel(EventRegistration registration) {
        boolean wasConfirmed = registration.getStatus() == RegistrationStatus.CONFIRMED;
        registration.cancel(clock.instant());
        if (wasConfirmed) {
            registrations.flush();
            promoteWaitlist(registration.getEvent());
        }
    }

    /** Sube de la lista de espera, en orden, a quienes caben en los cupos libres; avisa a cada uno. */
    private void promoteWaitlist(Event event) {
        if (event.getCapacity() == null && registrations.countByEventAndStatus(event, RegistrationStatus.WAITLISTED) == 0) {
            return;
        }
        while (true) {
            Optional<EventRegistration> next = registrations.findFirstByEventAndStatusOrderByWaitlistPositionAsc(event, RegistrationStatus.WAITLISTED);
            if (next.isEmpty()) {
                return;
            }
            OptionalInt left = event.seatsLeft(registrations.countConfirmedAttendees(event));
            if (left.isPresent() && left.getAsInt() < next.get().getAttendees()) {
                return;
            }
            EventRegistration promoted = next.get();
            promoted.promoteFromWaitlist();
            registrations.flush();
            audit.recordSystem(AuditAction.UPDATE, "EventRegistration", promoted.getId(), "Pasó de la lista de espera a confirmada");
            publisher.publishEvent(new OutgoingMail(promoted.getEmail(), "Se liberó un cupo: " + event.getTitle(), """
                    Hola %s:

                    Se liberó un cupo y tu inscripción a "%s" (%s) quedó confirmada. Si ya no puedes asistir,
                    cancela con el enlace del primer correo para que el lugar pase a otra familia.

                    %s
                    """.formatted(promoted.getName(), event.getTitle(), dates.of(event), schoolName())));
        }
    }

    private int nextWaitlistPosition(Event event) {
        return registrations.findFirstByEventAndStatusOrderByWaitlistPositionDesc(event, RegistrationStatus.WAITLISTED)
                .map(r -> r.getWaitlistPosition() + 1).orElse(1);
    }

    private String schoolName() {
        return schools.findSingleton().map(School::getName).orElse("El colegio");
    }

    /** Evita que Excel interprete una celda como fórmula y que un ";" desarme las columnas. */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
        return clean.matches("^[=+\\-@].*") ? "'" + clean : clean;
    }

    private static String required(String value, int max, String problem) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw new RuleViolation(problem);
        }
        return value.strip();
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new RuleViolation("Un campo supera el largo permitido");
        }
        return value.strip();
    }
}
