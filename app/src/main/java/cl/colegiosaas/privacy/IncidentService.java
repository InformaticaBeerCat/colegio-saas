package cl.colegiosaas.privacy;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.Formats;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Procedimiento ante brechas de seguridad (PRV-09): registrar qué pasó y cuándo se detectó, contener,
 * notificar a la Agencia y, si hay riesgo para las personas, a los titulares; cerrar con las medidas
 * tomadas. El panel muestra el tiempo transcurrido contra la meta de notificación y propone los textos.
 */
@Service
public class IncidentService {

    private final SecurityIncidentRepository incidents;
    private final UserAccountRepository users;
    private final PrivacyStaff staff;
    private final SchoolRepository schools;
    private final SchoolTime time;
    private final Formats formats;
    private final PrivacyProperties properties;
    private final AppProperties app;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    IncidentService(SecurityIncidentRepository incidents, UserAccountRepository users, PrivacyStaff staff,
                    SchoolRepository schools, SchoolTime time, Formats formats, PrivacyProperties properties,
                    AppProperties app, AuditTrail audit, ApplicationEventPublisher events, Clock clock) {
        this.incidents = incidents;
        this.users = users;
        this.staff = staff;
        this.schools = schools;
        this.time = time;
        this.formats = formats;
        this.properties = properties;
        this.app = app;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Datos editables de un incidente. La hora de detección viene en hora del colegio. */
    public record Details(String title, LocalDateTime detectedAt, IncidentSeverity severity, String affectedData,
                          Integer affectedSubjectsEstimate, String description, String actionsTaken) {
    }

    /** Tiempo transcurrido contra la meta de notificación a la Agencia. */
    public record Clockwork(Instant deadline, long hoursElapsed, boolean overdue) {
    }

    @Transactional(readOnly = true)
    public List<SecurityIncident> list() {
        return incidents.findAllByOrderByDetectedAtDesc();
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return incidents.countByStatusNot(IncidentStatus.CLOSED);
    }

    @Transactional(readOnly = true)
    public SecurityIncident get(long id) {
        return incidents.findWithReporterById(id).orElseThrow(() -> new NotFound("El incidente no existe"));
    }

    @Transactional
    public SecurityIncident register(Details details, long userId) {
        validate(details);
        Instant detectedAt = time.toInstant(details.detectedAt());
        if (detectedAt.isAfter(clock.instant().plus(Duration.ofMinutes(5)))) {
            throw new RuleViolation("La fecha de detección no puede estar en el futuro");
        }
        SecurityIncident incident = new SecurityIncident(details.title().strip(), detectedAt, details.severity(),
                users.findById(userId).orElseThrow());
        fill(incident, details);
        incidents.save(incident);
        audit.record(AuditAction.CREATE, "SecurityIncident", incident.getId(), incident.getTitle());
        for (String to : staff.emails()) {
            events.publishEvent(new OutgoingMail(to, "Incidente de seguridad registrado: " + incident.getTitle(), """
                    Se registró un incidente de seguridad (gravedad %s), detectado el %s.

                    Sigue el procedimiento en %s
                    """.formatted(incident.getSeverity(), formats.dateTime(detectedAt),
                    app.url("/admin/privacy/incidents/" + incident.getId()))));
        }
        return incident;
    }

    @Transactional
    public void update(long id, Details details) {
        validate(details);
        SecurityIncident incident = get(id);
        incident.setTitle(details.title().strip());
        incident.setSeverity(details.severity());
        fill(incident, details);
        audit.record(AuditAction.UPDATE, "SecurityIncident", id, incident.getTitle());
    }

    @Transactional
    public void contain(long id) {
        SecurityIncident incident = open(id);
        if (incident.getStatus() == IncidentStatus.CONTAINED) {
            throw new RuleViolation("El incidente ya está contenido");
        }
        incident.markContained(clock.instant());
        audit.record(AuditAction.UPDATE, "SecurityIncident", id, "Contenido");
    }

    @Transactional
    public void recordAuthorityNotification(long id, LocalDateTime at) {
        SecurityIncident incident = get(id);
        incident.recordAuthorityNotification(notInFuture(at));
        audit.record(AuditAction.UPDATE, "SecurityIncident", id, "Notificado a la Agencia");
    }

    @Transactional
    public void recordSubjectsNotification(long id, LocalDateTime at) {
        SecurityIncident incident = get(id);
        incident.recordSubjectsNotification(notInFuture(at));
        audit.record(AuditAction.UPDATE, "SecurityIncident", id, "Notificado a los titulares");
    }

    @Transactional
    public void close(long id) {
        SecurityIncident incident = open(id);
        if (incident.getActionsTaken() == null || incident.getActionsTaken().isBlank()) {
            throw new RuleViolation("Antes de cerrar, describe las medidas tomadas");
        }
        incident.close(clock.instant());
        audit.record(AuditAction.UPDATE, "SecurityIncident", id, "Cerrado");
    }

    public Clockwork clockwork(SecurityIncident incident) {
        Instant deadline = incident.getDetectedAt().plus(Duration.ofHours(properties.incidentHours()));
        Instant until = incident.getAuthorityNotifiedAt() != null ? incident.getAuthorityNotifiedAt() : clock.instant();
        long hours = Duration.between(incident.getDetectedAt(), until).toHours();
        return new Clockwork(deadline, hours, until.isAfter(deadline));
    }

    public int notificationHours() {
        return properties.incidentHours();
    }

    /** Borrador de la notificación a la Agencia de Protección de Datos Personales. */
    @Transactional(readOnly = true)
    public String authorityNotice(SecurityIncident incident) {
        School school = schools.findSingleton().orElse(null);
        return """
                Notificación de vulneración de seguridad de datos personales

                Responsable: %s%s
                Contacto: %s
                Fecha y hora de detección: %s
                Naturaleza del incidente: %s
                %s
                Datos afectados: %s
                Número estimado de titulares afectados: %s
                Medidas adoptadas o propuestas: %s
                """.formatted(
                school == null ? "[nombre del colegio]" : school.getName(),
                school == null || school.getRbd() == null ? "" : " (RBD " + school.getRbd() + ")",
                school == null || school.getContactEmail() == null ? "[correo de contacto]" : school.getContactEmail(),
                formats.dateTime(incident.getDetectedAt()),
                incident.getTitle(),
                or(incident.getDescription(), ""),
                or(incident.getAffectedData(), "[qué datos: emails, teléfonos, nombres de estudiantes…]"),
                incident.getAffectedSubjectsEstimate() == null ? "[estimación]" : incident.getAffectedSubjectsEstimate(),
                or(incident.getActionsTaken(), "[qué se hizo para contenerlo y evitar que se repita]")).strip();
    }

    /** Borrador del aviso a las personas afectadas, en lenguaje claro. */
    @Transactional(readOnly = true)
    public String subjectsNotice(SecurityIncident incident) {
        String schoolName = schools.findSingleton().map(School::getName).orElse("El colegio");
        return """
                Estimada familia:

                El %s detectamos un incidente de seguridad que pudo exponer algunos de sus datos personales: %s.

                Lo que hicimos: %s

                Le recomendamos desconfiar de correos o llamadas que pidan claves o pagos a nombre del colegio.
                Si tiene dudas, o quiere ejercer sus derechos sobre sus datos, escríbanos o use el formulario en %s

                %s
                """.formatted(formats.date(incident.getDetectedAt()),
                or(incident.getAffectedData(), "[qué datos]"),
                or(incident.getActionsTaken(), "[medidas tomadas]"),
                app.url("/privacidad/derechos"), schoolName).strip();
    }

    private SecurityIncident open(long id) {
        SecurityIncident incident = get(id);
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new RuleViolation("El incidente ya está cerrado");
        }
        return incident;
    }

    private Instant notInFuture(LocalDateTime at) {
        if (at == null) {
            throw new RuleViolation("Indica cuándo se notificó");
        }
        Instant instant = time.toInstant(at);
        if (instant.isAfter(clock.instant().plus(Duration.ofMinutes(5)))) {
            throw new RuleViolation("La fecha de notificación no puede estar en el futuro");
        }
        return instant;
    }

    private static void fill(SecurityIncident incident, Details details) {
        incident.setAffectedData(blankToNull(details.affectedData()));
        incident.setAffectedSubjectsEstimate(details.affectedSubjectsEstimate());
        incident.setDescription(blankToNull(details.description()));
        incident.setActionsTaken(blankToNull(details.actionsTaken()));
    }

    private static void validate(Details details) {
        if (details.title() == null || details.title().isBlank() || details.title().strip().length() > 200) {
            throw new RuleViolation("Describe el incidente en un título de hasta 200 caracteres");
        }
        if (details.detectedAt() == null) {
            throw new RuleViolation("Indica cuándo se detectó");
        }
        if (details.severity() == null) {
            throw new RuleViolation("Elige la gravedad");
        }
        if (details.affectedData() != null && details.affectedData().length() > 1000) {
            throw new RuleViolation("Los datos afectados pueden tener hasta 1.000 caracteres");
        }
        if (details.affectedSubjectsEstimate() != null && details.affectedSubjectsEstimate() < 0) {
            throw new RuleViolation("La estimación de personas afectadas no puede ser negativa");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
