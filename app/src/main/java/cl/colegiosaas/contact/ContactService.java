package cl.colegiosaas.contact;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.ConsentService;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.text.Emails;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Formulario de contacto con enrutamiento por área y número de ticket (COM-01), y la bandeja donde el
 * colegio responde, anota y cierra cada consulta midiendo el tiempo de primera respuesta (COM-02).
 */
@Service
public class ContactService {

    public static final Set<InquiryStatus> OPEN = EnumSet.of(InquiryStatus.NEW, InquiryStatus.IN_PROGRESS);

    private final ContactAreaRepository areas;
    private final InquiryRepository inquiries;
    private final ConsentService consents;
    private final UserAccountRepository users;
    private final SchoolRepository schools;
    private final AppProperties app;
    private final BlindIndex index;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ContactService(ContactAreaRepository areas, InquiryRepository inquiries, ConsentService consents,
                   UserAccountRepository users, SchoolRepository schools, AppProperties app, BlindIndex index,
                   AuditTrail audit, ApplicationEventPublisher events, Clock clock) {
        this.areas = areas;
        this.inquiries = inquiries;
        this.consents = consents;
        this.users = users;
        this.schools = schools;
        this.app = app;
        this.index = index;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // --- Formulario público ---------------------------------------------------------------------------------

    /** Lo que llega desde el sitio. {@code consent} es la casilla (separada, sin premarcar) del aviso de contacto. */
    public record Submission(Long areaId, String name, String email, String phone, String subject, String message,
                             boolean consent) {
    }

    @Transactional(readOnly = true)
    public List<ContactArea> publicAreas() {
        return areas.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional
    public Inquiry submit(Submission form, RequestOrigin origin) {
        ContactArea area = form.areaId() == null ? null
                : areas.findById(form.areaId()).filter(ContactArea::isActive).orElse(null);
        if (area == null) {
            throw new RuleViolation("Elige a quién va dirigido tu mensaje");
        }
        String name = required(form.name(), 150, "Escribe tu nombre (hasta 150 caracteres)");
        String email = form.email() == null ? "" : form.email().strip();
        if (!Emails.isValid(email)) {
            throw new RuleViolation("Escribe un correo válido: ahí te responderemos");
        }
        String phone = optional(form.phone(), 30, "El teléfono puede tener hasta 30 caracteres");
        String subject = optional(form.subject(), 150, "El asunto puede tener hasta 150 caracteres");
        String message = required(form.message(), 5000, "Escribe tu mensaje (hasta 5.000 caracteres)");
        if (!form.consent()) {
            throw new RuleViolation("Para responderte necesitamos tu autorización para usar estos datos: marca la casilla");
        }
        DataSubject sender = new DataSubject(name, email);
        ConsentRecord consent = consents.record(sender, ConsentPurpose.CONTACT, true, origin);
        Inquiry inquiry = inquiries.save(new Inquiry(area, sender, phone, subject, message, consent, index));
        audit.recordAnonymous(AuditAction.CREATE, "Inquiry", inquiry.getId(), inquiry.getTicketCode() + " → " + area.getName());

        events.publishEvent(new OutgoingMail(email, "Recibimos tu mensaje (" + inquiry.getTicketCode() + ")", """
                Hola %s:

                Recibimos tu mensaje para %s. Tu número de atención es %s; menciónalo si vuelves a escribirnos.
                Te responderemos a este correo.

                %s
                """.formatted(name, area.getName(), inquiry.getTicketCode(), schoolName())));
        notifyArea(area, inquiry, "Nueva consulta " + inquiry.getTicketCode());
        return inquiry;
    }

    // --- Bandeja ---------------------------------------------------------------------------------------------

    /** Grupo de estados que muestra la bandeja. */
    public enum Folder {
        OPEN(ContactService.OPEN),
        RESOLVED(EnumSet.of(InquiryStatus.RESOLVED)),
        SPAM(EnumSet.of(InquiryStatus.SPAM));

        private final Set<InquiryStatus> statuses;

        Folder(Set<InquiryStatus> statuses) {
            this.statuses = statuses;
        }
    }

    @Transactional(readOnly = true)
    public List<Inquiry> inbox(Long areaId, Folder folder) {
        if (areaId == null) {
            return inquiries.findTop200ByStatusInOrderByCreatedAtAsc(folder.statuses);
        }
        return inquiries.findTop200ByAreaAndStatusInOrderByCreatedAtAsc(area(areaId), folder.statuses);
    }

    @Transactional(readOnly = true)
    public long newCount() {
        return inquiries.countByStatus(InquiryStatus.NEW);
    }

    /** Promedio de horas hasta la primera respuesta en los últimos 30 días (COM-02). */
    @Transactional(readOnly = true)
    public OptionalDouble averageFirstResponseHours() {
        return inquiries.findByFirstResponseAtAfter(clock.instant().minus(Duration.ofDays(30))).stream()
                .flatMap(i -> i.timeToFirstResponse().stream())
                .mapToDouble(d -> d.toMinutes() / 60.0)
                .average();
    }

    /** Nota interna con su autor, armada dentro de la transacción para la vista. */
    public record NoteView(String author, Instant createdAt, String body) {
    }

    /** Una consulta abierta en el panel: lo que se ve sin volver a la base de datos. */
    public record InquiryView(Inquiry inquiry, List<NoteView> notes, String consentText) {
    }

    @Transactional
    public InquiryView view(long id) {
        Inquiry inquiry = get(id);
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "Inquiry", id, inquiry.getTicketCode());
        List<NoteView> notes = inquiry.getNotes().stream()
                .map(n -> new NoteView(n.getAuthor() == null ? "Sistema" : n.getAuthor().getName(), n.getCreatedAt(), n.getBody()))
                .toList();
        ConsentRecord consent = inquiry.getConsent();
        String consentText = consent.getLegalText().getTitle() + " (versión " + consent.getLegalText().getVersionNumber() + ")";
        return new InquiryView(inquiry, notes, consentText);
    }

    /** Responde por correo desde el panel: cuenta como primera respuesta y queda como nota. */
    @Transactional
    public void reply(long id, String text, long userId) {
        Inquiry inquiry = get(id);
        String body = required(text, 5000, "Escribe la respuesta");
        if (inquiry.getStatus() == InquiryStatus.SPAM || inquiry.isAnonymized()) {
            throw new RuleViolation("Esta consulta no admite respuesta");
        }
        UserAccount author = user(userId);
        inquiry.recordResponse(clock.instant());
        inquiry.addNote(author, "Respuesta enviada:\n" + body);
        audit.record(AuditAction.UPDATE, "Inquiry", id, "Respuesta enviada");
        events.publishEvent(new OutgoingMail(inquiry.getEmail(),
                "Respuesta a tu mensaje (" + inquiry.getTicketCode() + ")", body + "\n\n" + author.getName() + "\n" + schoolName()));
    }

    @Transactional
    public void addNote(long id, String text, long userId) {
        Inquiry inquiry = get(id);
        inquiry.addNote(user(userId), required(text, 5000, "Escribe la nota"));
        audit.record(AuditAction.UPDATE, "Inquiry", id, "Nota interna");
    }

    @Transactional
    public void assignToMe(long id, long userId) {
        get(id).assignTo(user(userId));
        audit.record(AuditAction.UPDATE, "Inquiry", id, "Asignada");
    }

    /** Deriva la consulta a otra área, que recibe el aviso. */
    @Transactional
    public void route(long id, long areaId) {
        Inquiry inquiry = get(id);
        ContactArea target = area(areaId);
        if (target.equals(inquiry.getArea())) {
            return;
        }
        inquiry.routeTo(target);
        audit.record(AuditAction.UPDATE, "Inquiry", id, "Derivada a " + target.getName());
        notifyArea(target, inquiry, "Consulta derivada " + inquiry.getTicketCode());
    }

    @Transactional
    public void resolve(long id) {
        get(id).resolve(clock.instant());
        audit.record(AuditAction.UPDATE, "Inquiry", id, "Resuelta");
    }

    @Transactional
    public void markAsSpam(long id) {
        get(id).markAsSpam();
        audit.record(AuditAction.UPDATE, "Inquiry", id, "Spam");
    }

    @Transactional
    public void reopen(long id) {
        get(id).reopen();
        audit.record(AuditAction.UPDATE, "Inquiry", id, "Reabierta");
    }

    // --- Áreas -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ContactArea> allAreas() {
        return areas.findAll().stream().sorted(java.util.Comparator.comparingInt(ContactArea::getSortOrder)).toList();
    }

    @Transactional
    public ContactArea saveArea(Long id, String name, String notifyEmail, String description, int sortOrder, boolean active) {
        String cleanName = required(name, 80, "El área necesita un nombre de hasta 80 caracteres");
        String email = notifyEmail == null ? "" : notifyEmail.strip();
        if (!Emails.isValid(email)) {
            throw new RuleViolation("Escribe el correo que recibirá los avisos del área");
        }
        Optional<ContactArea> sameName = areas.findAll().stream()
                .filter(a -> a.getName().equalsIgnoreCase(cleanName) && !a.getId().equals(id)).findFirst();
        if (sameName.isPresent()) {
            throw new RuleViolation("Ya existe un área con ese nombre");
        }
        ContactArea area = id == null ? areas.save(new ContactArea(cleanName, email, sortOrder)) : area(id);
        area.setName(cleanName);
        area.setNotifyEmail(email);
        area.setDescription(optional(description, 300, "La descripción puede tener hasta 300 caracteres"));
        area.setSortOrder(sortOrder);
        if (!active && area.isActive() && areas.findByActiveTrueOrderBySortOrderAsc().size() == 1) {
            throw new RuleViolation("Debe quedar al menos un área activa para recibir mensajes");
        }
        area.setActive(active);
        audit.record(id == null ? AuditAction.CREATE : AuditAction.UPDATE, "ContactArea", area.getId(), cleanName);
        return area;
    }

    // --- Apoyo -----------------------------------------------------------------------------------------------

    private void notifyArea(ContactArea area, Inquiry inquiry, String subject) {
        // Al área le llega el aviso sin los datos de la persona: se leen en el panel, con registro de acceso.
        events.publishEvent(new OutgoingMail(area.getNotifyEmail(), subject, """
                Hay una consulta para %s%s.

                Ábrela en %s
                """.formatted(area.getName(), inquiry.getSubject() == null ? "" : " (asunto: " + inquiry.getSubject() + ")",
                app.url("/admin/inquiries/" + inquiry.getId()))));
    }

    private Inquiry get(long id) {
        return inquiries.findWithAreaById(id).orElseThrow(() -> new NotFound("La consulta no existe"));
    }

    private ContactArea area(long id) {
        return areas.findById(id).orElseThrow(() -> new NotFound("El área no existe"));
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow();
    }

    private String schoolName() {
        return schools.findSingleton().map(School::getName).orElse("El colegio");
    }

    private static String required(String value, int max, String problem) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw new RuleViolation(problem);
        }
        return value.strip();
    }

    private static String optional(String value, int max, String problem) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new RuleViolation(problem);
        }
        return value.strip();
    }
}
