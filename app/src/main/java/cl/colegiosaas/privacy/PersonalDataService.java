package cl.colegiosaas.privacy;

import cl.colegiosaas.admissions.Prospect;
import cl.colegiosaas.admissions.ProspectRepository;
import cl.colegiosaas.audit.AuditAction;
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
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Todo lo que el colegio guarda de una persona, buscado por el índice ciego de su email (PRV-07): para
 * responder una solicitud de acceso o portabilidad (exportar en JSON) o de supresión (borrar).
 */
@Service
public class PersonalDataService {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final ConsentRecordRepository consents;
    private final DataSubjectRequestRepository requests;
    private final InquiryRepository inquiries;
    private final ProspectRepository prospects;
    private final AppointmentRepository appointments;
    private final EventRegistrationRepository registrations;
    private final StudentRepository students;
    private final SchoolTime time;
    private final BlindIndex index;
    private final AuditTrail audit;

    PersonalDataService(ConsentRecordRepository consents, DataSubjectRequestRepository requests, InquiryRepository inquiries,
                        ProspectRepository prospects, AppointmentRepository appointments,
                        EventRegistrationRepository registrations, StudentRepository students, SchoolTime time,
                        BlindIndex index, AuditTrail audit) {
        this.consents = consents;
        this.requests = requests;
        this.inquiries = inquiries;
        this.prospects = prospects;
        this.appointments = appointments;
        this.registrations = registrations;
        this.students = students;
        this.time = time;
        this.index = index;
        this.audit = audit;
    }

    /** Un registro, como lo ve el encargado y como sale en la exportación. */
    public record Entry(long id, Instant createdAt, Map<String, String> fields) {
    }

    /**
     * Un tipo de dato.
     *
     * @param onErase qué le pasa a estos registros al suprimir
     */
    public record Section(String key, String title, String onErase, List<Entry> entries) {
    }

    public record Dossier(String email, List<Section> sections) {

        public int total() {
            return sections.stream().mapToInt(s -> s.entries().size()).sum();
        }
    }

    /** Resumen de una supresión, para informar al titular. */
    public record Erasure(int deleted, int anonymized, int detached) {
    }

    @Transactional
    public Dossier find(String email) {
        String hash = hashOf(email);
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "DataSubject", hash.substring(0, 12), "Búsqueda de datos de una persona");
        return dossier(email.strip(), hash);
    }

    /** Exportación en JSON legible y reutilizable (acceso y portabilidad). */
    @Transactional
    public String exportJson(String email) {
        String hash = hashOf(email);
        Dossier dossier = dossier(email.strip(), hash);
        audit.record(AuditAction.EXPORT, "DataSubject", hash.substring(0, 12), dossier.total() + " registros exportados");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("email", dossier.email());
        out.put("exportadoEl", time.now().toString());
        for (Section section : dossier.sections()) {
            out.put(section.key(), section.entries().stream().map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.id());
                row.put("creado", e.createdAt() == null ? null : e.createdAt().toString());
                row.putAll(e.fields());
                return row;
            }).toList());
        }
        return JSON.writeValueAsString(out);
    }

    /**
     * Supresión (PRV-07): borra consultas, citas, inscripciones y registros de admisión; quita el email de
     * apoderado de los estudiantes y anonimiza los consentimientos (queda la prueba de qué se aceptó, sin
     * saber quién). Las solicitudes de derechos se conservan para acreditar que se respondieron; las
     * anonimiza la retención.
     */
    @Transactional
    public Erasure erase(String email) {
        String hash = hashOf(email);
        int deleted = 0;
        List<Inquiry> ownInquiries = inquiries.findByEmailHash(hash);
        inquiries.deleteAll(ownInquiries);
        deleted += ownInquiries.size();
        List<Appointment> ownAppointments = appointments.findByContactEmailHash(hash);
        appointments.deleteAll(ownAppointments);
        deleted += ownAppointments.size();
        List<EventRegistration> ownRegistrations = registrations.findByEmailHash(hash);
        registrations.deleteAll(ownRegistrations);
        deleted += ownRegistrations.size();
        List<Prospect> ownProspects = prospects.findByEmailHash(hash);
        prospects.deleteAll(ownProspects);
        deleted += ownProspects.size();

        List<Student> children = students.findByGuardianEmailHash(hash);
        children.forEach(s -> s.changeGuardianEmail(null, index));

        List<ConsentRecord> ownConsents = consents.findBySubjectEmailHashOrderByCreatedAtDesc(hash);
        ownConsents.forEach(ConsentRecord::anonymize);

        Erasure result = new Erasure(deleted, ownConsents.size(), children.size());
        audit.record(AuditAction.DELETE, "DataSubject", hash.substring(0, 12),
                "Supresión: %d borrados, %d consentimientos anonimizados, %d estudiantes sin email de apoderado"
                        .formatted(result.deleted(), result.anonymized(), result.detached()));
        return result;
    }

    private Dossier dossier(String email, String hash) {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("consentimientos", "Consentimientos", "Se anonimizan",
                consents.findBySubjectEmailHashOrderByCreatedAtDesc(hash).stream().map(c -> entry(c.getId(), c.getCreatedAt(),
                        "Finalidad", c.getPurpose().name(),
                        "Texto aceptado", c.getLegalText().getKind().slug() + " v" + c.getLegalText().getVersionNumber(),
                        "Respuesta", c.isGranted() ? "Sí" : "No",
                        "Nombre", c.getSubjectName(),
                        "Origen", c.getSource(),
                        "IP", c.getIpAddress(),
                        "Retirado", str(c.getWithdrawnAt()))).toList()));
        sections.add(new Section("solicitudesDeDerechos", "Solicitudes de derechos", "Se conservan como prueba de respuesta",
                requests.findByRequesterEmailHashOrderByCreatedAtDesc(hash).stream().map(r -> entry(r.getId(), r.getCreatedAt(),
                        "Código", r.getTrackingCode(),
                        "Derecho", r.getRequestedRight().name(),
                        "Estado", r.getStatus().name(),
                        "Nombre", r.getRequesterName(),
                        "En nombre de un menor", r.isOnBehalfOfMinor() ? "Sí" : "No",
                        "Detalle", r.getDetails(),
                        "Respuesta", r.getResolution())).toList()));
        sections.add(new Section("consultas", "Consultas de contacto", "Se borran",
                inquiries.findByEmailHash(hash).stream().map(i -> entry(i.getId(), i.getCreatedAt(),
                        "Ticket", i.getTicketCode(),
                        "Área", i.getArea().getName(),
                        "Nombre", i.getName(),
                        "Teléfono", i.getPhone(),
                        "Asunto", i.getSubject(),
                        "Mensaje", i.getMessage(),
                        "Estado", i.getStatus().name())).toList()));
        sections.add(new Section("citas", "Citas agendadas", "Se borran",
                appointments.findByContactEmailHash(hash).stream().map(a -> entry(a.getId(), a.getCreatedAt(),
                        "Tipo", a.getAppointmentType().getName(),
                        "Fecha", a.getStartsAt().toString(),
                        "Nombre", a.getContactName(),
                        "Teléfono", a.getContactPhone(),
                        "Estudiante", a.getStudentName(),
                        "Estado", a.getStatus().name())).toList()));
        sections.add(new Section("inscripciones", "Inscripciones a eventos", "Se borran",
                registrations.findByEmailHash(hash).stream().map(r -> entry(r.getId(), r.getCreatedAt(),
                        "Evento", r.getEvent().getTitle(),
                        "Nombre", r.getName(),
                        "Teléfono", r.getPhone(),
                        "Asistentes", String.valueOf(r.getAttendees()),
                        "Estudiante", r.getStudentName(),
                        "Estado", r.getStatus().name())).toList()));
        sections.add(new Section("admision", "Registros de interés en admisión", "Se borran",
                prospects.findByEmailHash(hash).stream().map(p -> entry(p.getId(), p.getCreatedAt(),
                        "Nombre", p.getGuardianName(),
                        "Teléfono", p.getPhone(),
                        "Nivel", p.getGradeLevel() == null ? null : p.getGradeLevel().getName(),
                        "Año de ingreso", p.getEntryYear() == null ? null : String.valueOf(p.getEntryYear()),
                        "Etapa", p.getStage().name(),
                        "Notas", p.getNotes())).toList()));
        sections.add(new Section("estudiantes", "Estudiantes a cargo (autorizaciones de imagen)", "Se quita el email de apoderado",
                students.findByGuardianEmailHash(hash).stream().map(s -> entry(s.getId(), s.getCreatedAt(),
                        "Estudiante", s.getFullName(),
                        "Curso", s.getCourse().displayName(),
                        "Activo", s.isActive() ? "Sí" : "No")).toList()));
        return new Dossier(email, sections);
    }

    private String hashOf(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new RuleViolation("Escribe el email de la persona");
        }
        return index.of(email);
    }

    private static Entry entry(long id, Instant createdAt, String... pairs) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                fields.put(pairs[i], pairs[i + 1]);
            }
        }
        return new Entry(id, createdAt, fields);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
