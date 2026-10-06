package cl.colegiosaas.privacy;

import cl.colegiosaas.admissions.Prospect;
import cl.colegiosaas.admissions.ProspectRepository;
import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditLogRepository;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.calendar.EventRegistration;
import cl.colegiosaas.calendar.EventRegistrationRepository;
import cl.colegiosaas.consent.Student;
import cl.colegiosaas.consent.StudentRepository;
import cl.colegiosaas.contact.Inquiry;
import cl.colegiosaas.contact.InquiryRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.scheduling.Appointment;
import cl.colegiosaas.scheduling.AppointmentRepository;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Plazos de conservación (PRV-06): una tarea diaria borra o anonimiza lo que venció según la política de
 * cada tipo de dato. Lo que se cuenta es la antigüedad desde el hecho que cierra el uso del dato: el fin de
 * la cita o del evento, el cierre de la solicitud, la salida del estudiante.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final RetentionPolicyRepository policies;
    private final InquiryRepository inquiries;
    private final ProspectRepository prospects;
    private final AppointmentRepository appointments;
    private final EventRegistrationRepository registrations;
    private final DataSubjectRequestRepository requests;
    private final ConsentRecordRepository consents;
    private final StudentRepository students;
    private final AuditLogRepository auditLog;
    private final SchoolTime time;
    private final PrivacyProperties properties;
    private final AuditTrail audit;
    private final Clock clock;

    RetentionService(RetentionPolicyRepository policies, InquiryRepository inquiries, ProspectRepository prospects,
                     AppointmentRepository appointments, EventRegistrationRepository registrations,
                     DataSubjectRequestRepository requests, ConsentRecordRepository consents, StudentRepository students,
                     AuditLogRepository auditLog, SchoolTime time, PrivacyProperties properties, AuditTrail audit,
                     Clock clock) {
        this.policies = policies;
        this.inquiries = inquiries;
        this.prospects = prospects;
        this.appointments = appointments;
        this.registrations = registrations;
        this.requests = requests;
        this.consents = consents;
        this.students = students;
        this.auditLog = auditLog;
        this.time = time;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RetentionPolicy> policies() {
        return policies.findAll().stream().sorted(Comparator.comparing(p -> p.getDataCategory().ordinal())).toList();
    }

    @Transactional
    public void update(long id, int days, RetentionAction action) {
        RetentionPolicy policy = policies.findById(id).orElseThrow(() -> new NotFound("La política no existe"));
        if (days < 1 || days > 36500) {
            throw new RuleViolation("El plazo debe estar entre 1 día y 100 años");
        }
        if (action == null || !policy.getDataCategory().allowedActions().contains(action)) {
            throw new RuleViolation("Esa acción no corresponde a este tipo de dato");
        }
        policy.setRetentionDays(days);
        policy.setAction(action);
        audit.record(AuditAction.UPDATE, "RetentionPolicy", id, policy.getDataCategory() + ": " + days + " días, " + action);
    }

    /** Tarea diaria, de madrugada en el servidor. Se puede apagar con {@code app.privacy.retention-enabled}. */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void scheduledRun() {
        if (!properties.retentionEnabled()) {
            return;
        }
        Map<RetentionCategory, Integer> done = apply();
        if (done.values().stream().anyMatch(n -> n > 0)) {
            log.info("Retención aplicada: {}", done);
        }
    }

    /** Aplica todas las políticas ahora. Devuelve cuántos registros se borraron o anonimizaron por tipo. */
    @Transactional
    public Map<RetentionCategory, Integer> apply() {
        Map<RetentionCategory, Integer> done = new EnumMap<>(RetentionCategory.class);
        for (RetentionPolicy policy : policies.findAll()) {
            int count = apply(policy);
            done.put(policy.getDataCategory(), count);
            if (count > 0) {
                audit.recordSystem(policy.getAction() == RetentionAction.DELETE ? AuditAction.DELETE : AuditAction.UPDATE,
                        "RetentionPolicy", policy.getId(), "Retención %s: %d registro(s) %s".formatted(policy.getDataCategory(),
                                count, policy.getAction() == RetentionAction.DELETE ? "borrados" : "anonimizados"));
            }
        }
        return done;
    }

    private int apply(RetentionPolicy policy) {
        Instant cutoff = clock.instant().minus(Duration.ofDays(policy.getRetentionDays()));
        LocalDateTime localCutoff = time.toLocal(cutoff);
        boolean anonymize = policy.getAction() == RetentionAction.ANONYMIZE;
        return switch (policy.getDataCategory()) {
            case INQUIRIES -> {
                List<Inquiry> due = inquiries.findByCreatedAtBeforeAndEmailHashNot(cutoff, Anonymized.EMAIL_HASH);
                if (anonymize) {
                    due.forEach(Inquiry::anonymize);
                } else {
                    inquiries.deleteAll(due);
                }
                yield due.size();
            }
            case PROSPECTS -> {
                // Cada registro trae su propio plazo (se puede extender caso a caso); se usa el más largo de los dos.
                List<Prospect> due = prospects.findByRetainUntilBefore(time.today()).stream()
                        .filter(p -> p.getCreatedAt().isBefore(cutoff)).toList();
                prospects.deleteAll(due);
                yield due.size();
            }
            case APPOINTMENTS -> {
                List<Appointment> due = appointments.findByEndsAtBefore(localCutoff);
                appointments.deleteAll(due);
                yield due.size();
            }
            case EVENT_REGISTRATIONS -> {
                List<EventRegistration> due = registrations.findByEvent_EndsAtBefore(localCutoff);
                registrations.deleteAll(due);
                yield due.size();
            }
            case DATA_SUBJECT_REQUESTS -> {
                List<DataSubjectRequest> due = requests.findByStatusInAndResolvedAtBeforeAndRequesterEmailHashNot(
                        DataSubjectRequestService.CLOSED, cutoff, Anonymized.EMAIL_HASH);
                due.forEach(DataSubjectRequest::anonymize);
                yield due.size();
            }
            case CONSENT_RECORDS -> {
                // Un boletín aceptado y no retirado sigue vigente: no se toca aunque sea antiguo.
                List<ConsentRecord> due = consents.findByCreatedAtBeforeAndSubjectEmailHashNot(cutoff, Anonymized.EMAIL_HASH)
                        .stream().filter(c -> !(c.getPurpose().isOngoing() && c.isActive())).toList();
                due.forEach(ConsentRecord::anonymize);
                yield due.size();
            }
            case STUDENTS -> {
                // Las autorizaciones y etiquetas del estudiante se borran en cascada en la base de datos.
                List<Student> due = students.findByActiveFalseAndUpdatedAtBefore(cutoff);
                students.deleteAll(due);
                yield due.size();
            }
            case AUDIT_LOG -> auditLog.deleteOlderThan(cutoff);
        };
    }
}
