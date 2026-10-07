package cl.colegiosaas.scheduling;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.calendar.IcsWriter;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Reserva de citas desde el sitio (AGE-03), confirmación con el archivo de calendario y recordatorio (AGE-04),
 * reprogramar o cancelar con el enlace secreto del correo (AGE-05) y el panel del gestor (AGE-10).
 */
@Service
public class BookingService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-CL"));

    private final AppointmentTypeRepository types;
    private final AppointmentRepository appointments;
    private final UserAccountRepository users;
    private final SlotFinder slots;
    private final ConsentService consents;
    private final SchoolRepository schools;
    private final SchoolTime time;
    private final AgendaProperties properties;
    private final AppProperties app;
    private final BlindIndex index;
    private final AuditTrail audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    BookingService(AppointmentTypeRepository types, AppointmentRepository appointments, UserAccountRepository users,
                   SlotFinder slots, ConsentService consents, SchoolRepository schools, SchoolTime time,
                   AgendaProperties properties, AppProperties app, BlindIndex index, AuditTrail audit,
                   ApplicationEventPublisher publisher, Clock clock) {
        this.types = types;
        this.appointments = appointments;
        this.users = users;
        this.slots = slots;
        this.consents = consents;
        this.schools = schools;
        this.time = time;
        this.properties = properties;
        this.app = app;
        this.index = index;
        this.audit = audit;
        this.publisher = publisher;
        this.clock = clock;
    }

    // --- Sitio público ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AppointmentType> publicTypes() {
        return types.findWithHostsByActiveTrueOrderByNameAsc().stream().filter(t -> !t.getHosts().isEmpty()).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentType publicType(long id) {
        return types.findWithHostsById(id).filter(AppointmentType::isActive)
                .orElseThrow(() -> new NotFound("Este tipo de cita no existe"));
    }

    @Transactional(readOnly = true)
    public List<SlotFinder.Slot> slotsFor(long typeId) {
        return slots.available(publicType(typeId), null);
    }

    /** Lo que llega del formulario de reserva. */
    public record Request(long hostId, LocalDateTime start, MeetingMode mode, String name, String email, String phone,
                          String studentName, boolean consent) {
    }

    /** Cita reservada y el enlace secreto para gestionarla (solo existe en el correo). */
    public record Booked(Appointment appointment, String manageUrl) {
    }

    @Transactional
    public Booked book(long typeId, Request request, RequestOrigin origin) {
        AppointmentType type = publicType(typeId);
        String name = required(request.name(), 150, "Escribe tu nombre (hasta 150 caracteres)");
        String email = request.email() == null ? "" : request.email().strip();
        if (!Emails.isValid(email)) {
            throw new RuleViolation("Escribe un correo válido: ahí te llega la confirmación");
        }
        String studentName = optional(request.studentName(), 150);
        if (type.getAudience() == AppointmentAudience.GUARDIAN && studentName == null) {
            throw new RuleViolation("Indica el nombre del estudiante");
        }
        MeetingMode mode = request.mode() != null ? request.mode() : (type.isInPersonAllowed() ? MeetingMode.IN_PERSON : MeetingMode.ONLINE);
        if (!type.allows(mode)) {
            throw new RuleViolation("Esta cita no se ofrece en esa modalidad");
        }
        if (!request.consent()) {
            throw new RuleViolation("Para agendar necesitamos tu autorización para usar estos datos: marca la casilla");
        }
        // Con la fila del funcionario bloqueada se revisa otra vez que la hora siga libre.
        UserAccount host = users.findForUpdate(request.hostId()).filter(type::isHostedBy)
                .orElseThrow(() -> new RuleViolation("Elige una hora de la lista"));
        if (request.start() == null || !slots.isAvailable(type, host, request.start(), null)) {
            throw new RuleViolation("Esa hora ya no está disponible. Elige otra, por favor.");
        }
        DataSubject person = new DataSubject(name, email);
        ConsentRecord consent = consents.record(person, ConsentPurpose.SCHEDULING, true, origin);
        Appointment.Booking booking = Appointment.book(type, host, request.start(), mode, person, optional(request.phone(), 30),
                consent, index);
        Appointment appointment = booking.appointment();
        appointment.setStudentName(studentName);
        appointments.save(appointment);
        audit.recordAnonymous(AuditAction.CREATE, "Appointment", appointment.getId(), type.getName() + " " + request.start());

        String manageUrl = app.url("/citas/" + booking.manageToken());
        publisher.publishEvent(new OutgoingMail(email, "Cita confirmada: " + type.getName(), """
                Hola %s:

                Tu cita quedó agendada.

                %s
                Cuándo: %s
                Con: %s
                Modalidad: %s

                Agrégala a tu calendario: %s
                Si necesitas cambiar la hora o cancelar, usa este enlace (es personal, no lo compartas): %s

                %s
                """.formatted(name, type.getName(), when(appointment.getStartsAt()), host.getName(), modeLabel(mode),
                manageUrl + "/cita.ics", manageUrl, schoolName())));
        publisher.publishEvent(new OutgoingMail(host.getEmail(), "Nueva cita: " + type.getName() + ", " + when(appointment.getStartsAt()), """
                Se agendó una cita contigo: %s, %s (%s).

                Revisa tu agenda en %s
                """.formatted(type.getName(), when(appointment.getStartsAt()), modeLabel(mode), app.url("/admin/scheduling"))));
        return new Booked(appointment, manageUrl);
    }

    // --- Enlace secreto (AGE-05) -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Appointment byToken(String token) {
        return appointments.findWithTypeByManageTokenHash(SecureTokens.hash(token == null ? "" : token))
                .orElseThrow(() -> new NotFound("El enlace no es válido"));
    }

    /** Horas a las que se puede mover la cita: mismo tipo y mismo funcionario. */
    @Transactional(readOnly = true)
    public List<SlotFinder.Slot> rescheduleOptions(String token) {
        Appointment appointment = byToken(token);
        return slots.available(types.findWithHostsById(appointment.getAppointmentType().getId()).orElseThrow(), appointment.getHost());
    }

    @Transactional
    public void rescheduleByToken(String token, LocalDateTime newStart) {
        Appointment appointment = changeable(token);
        UserAccount host = users.findForUpdate(appointment.getHost().getId()).orElseThrow();
        AppointmentType type = types.findWithHostsById(appointment.getAppointmentType().getId()).orElseThrow();
        if (newStart == null || !slots.isAvailable(type, host, newStart, appointment.getId())) {
            throw new RuleViolation("Esa hora ya no está disponible. Elige otra, por favor.");
        }
        LocalDateTime before = appointment.getStartsAt();
        appointment.reschedule(newStart);
        appointment.setReminderSentAt(null);
        audit.recordAnonymous(AuditAction.UPDATE, "Appointment", appointment.getId(), "Reprogramada por la persona");
        String manageUrl = app.url("/citas/" + token);
        publisher.publishEvent(new OutgoingMail(appointment.getContactEmail(), "Cita reprogramada: " + type.getName(), """
                Hola %s:

                Tu cita cambió de hora. Ahora es el %s con %s.

                Actualiza tu calendario: %s
                Para cambiarla o cancelarla otra vez: %s

                %s
                """.formatted(appointment.getContactName(), when(newStart), host.getName(), manageUrl + "/cita.ics", manageUrl, schoolName())));
        publisher.publishEvent(new OutgoingMail(host.getEmail(), "Cita reprogramada: " + type.getName(), """
                La cita de %s pasó al %s.
                """.formatted(when(before), when(newStart))));
    }

    @Transactional
    public void cancelByToken(String token, String reason) {
        Appointment appointment = changeable(token);
        String cleanReason = optional(reason, 500);
        appointment.cancel(cleanReason, clock.instant());
        audit.recordAnonymous(AuditAction.UPDATE, "Appointment", appointment.getId(), "Cancelada por la persona");
        publisher.publishEvent(new OutgoingMail(appointment.getHost().getEmail(), "Cita cancelada: " + appointment.getAppointmentType().getName(), """
                Se canceló la cita del %s.%s
                """.formatted(when(appointment.getStartsAt()), cleanReason == null ? "" : "\nMotivo: " + cleanReason)));
    }

    /** Archivo de calendario de la cita: con el mismo UID, al reprogramar se actualiza en vez de duplicarse. */
    @Transactional(readOnly = true)
    public String ics(String token) {
        Appointment appointment = byToken(token);
        String host = app.baseUrl().replaceFirst("^https?://", "").replaceFirst("[:/].*$", "");
        return IcsWriter.single(schoolName(), "cita-" + appointment.getId() + "@" + host,
                appointment.getAppointmentType().getName() + " · " + schoolName(),
                appointment.getMode() == MeetingMode.IN_PERSON ? schoolName() : null,
                "Con " + appointment.getHost().getName() + ". Para cambiarla: " + app.url("/citas/" + token),
                appointment.getStartsAt(), appointment.getEndsAt(), time.zone(), clock.instant());
    }

    // --- Recordatorios (AGE-04) ------------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    @Transactional
    public void scheduledReminders() {
        sendReminders();
    }

    /** Envía el recordatorio de las citas que empiezan dentro del plazo configurado; devuelve cuántos envió. */
    @Transactional
    public int sendReminders() {
        LocalDateTime now = time.now();
        List<Appointment> due = appointments.findByStatusAndReminderSentAtIsNullAndStartsAtBetween(
                AppointmentStatus.CONFIRMED, now, now.plus(properties.reminderLead()));
        for (Appointment appointment : due) {
            publisher.publishEvent(new OutgoingMail(appointment.getContactEmail(),
                    "Recordatorio: " + appointment.getAppointmentType().getName(), """
                    Hola %s:

                    Te recordamos tu cita: %s, %s, con %s.

                    Si no puedes asistir, cancélala o cámbiala con el enlace del correo de confirmación, así la hora
                    queda libre para otra familia.

                    %s
                    """.formatted(appointment.getContactName(), appointment.getAppointmentType().getName(),
                    when(appointment.getStartsAt()), appointment.getHost().getName(), schoolName())));
            appointment.setReminderSentAt(clock.instant());
        }
        return due.size();
    }

    // --- Panel del gestor (AGE-10) ---------------------------------------------------------------------------

    /** @param hostId nulo = de todos los funcionarios (permiso de agenda general) */
    @Transactional(readOnly = true)
    public List<Appointment> agenda(Long hostId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return hostId == null ? appointments.findByStartsAtBetweenOrderByStartsAtAsc(start, end)
                : appointments.findByHostAndStartsAtBetweenOrderByStartsAtAsc(user(hostId), start, end);
    }

    /** Abre una cita en el panel: quedan registrados el acceso a los datos de la familia. */
    @Transactional
    public Appointment view(long id, Long restrictToHost) {
        Appointment appointment = staffAppointment(id, restrictToHost);
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "Appointment", id, appointment.getAppointmentType().getName());
        return appointment;
    }

    @Transactional
    public void markAttended(long id, Long restrictToHost) {
        Appointment appointment = staffAppointment(id, restrictToHost);
        change(appointment::markAttended);
        audit.record(AuditAction.UPDATE, "Appointment", id, "Asistió");
    }

    @Transactional
    public void markNoShow(long id, Long restrictToHost) {
        Appointment appointment = staffAppointment(id, restrictToHost);
        change(appointment::markNoShow);
        audit.record(AuditAction.UPDATE, "Appointment", id, "No asistió");
    }

    /** El colegio cancela: la familia recibe el motivo y la invitación a reservar otra hora. */
    @Transactional
    public void cancelByStaff(long id, String reason, Long restrictToHost) {
        Appointment appointment = staffAppointment(id, restrictToHost);
        String cleanReason = required(reason, 500, "Indica el motivo: se lo enviamos a la familia");
        change(() -> appointment.cancel(cleanReason, clock.instant()));
        audit.record(AuditAction.UPDATE, "Appointment", id, "Cancelada por el colegio");
        publisher.publishEvent(new OutgoingMail(appointment.getContactEmail(), "Cita cancelada: " + appointment.getAppointmentType().getName(), """
                Hola %s:

                Lamentamos avisarte que tu cita del %s fue cancelada.
                Motivo: %s

                Puedes reservar otra hora en %s

                %s
                """.formatted(appointment.getContactName(), when(appointment.getStartsAt()), cleanReason,
                app.url("/agenda/" + appointment.getAppointmentType().getId()), schoolName())));
    }

    @Transactional
    public void saveNotes(long id, String notes, Long restrictToHost) {
        Appointment appointment = staffAppointment(id, restrictToHost);
        String clean = notes == null || notes.isBlank() ? null : notes.strip();
        if (clean != null && clean.length() > 1000) {
            throw new RuleViolation("Las notas pueden tener hasta 1.000 caracteres");
        }
        appointment.setStaffNotes(clean);
        audit.record(AuditAction.UPDATE, "Appointment", id, "Notas");
    }

    // --- Apoyo -----------------------------------------------------------------------------------------------

    public static String when(LocalDateTime at) {
        return WHEN.format(at);
    }

    private Appointment changeable(String token) {
        Appointment appointment = byToken(token);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuleViolation("Esta cita ya no está vigente");
        }
        if (!appointment.getStartsAt().isAfter(time.now())) {
            throw new RuleViolation("La cita ya pasó");
        }
        return appointment;
    }

    private Appointment staffAppointment(long id, Long restrictToHost) {
        Appointment appointment = appointments.findWithTypeById(id).orElseThrow(() -> new NotFound("La cita no existe"));
        if (restrictToHost != null && !appointment.getHost().getId().equals(restrictToHost)) {
            throw new NotFound("La cita no existe");
        }
        return appointment;
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow(() -> new NotFound("El funcionario no existe"));
    }

    private String schoolName() {
        return schools.findSingleton().map(School::getName).orElse("El colegio");
    }

    private static String modeLabel(MeetingMode mode) {
        return mode == MeetingMode.IN_PERSON ? "presencial, en el colegio" : "en línea (te enviaremos el enlace)";
    }

    private static void change(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException e) {
            throw new RuleViolation(e.getMessage());
        }
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
