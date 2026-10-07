package cl.colegiosaas.privacy;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.Formats;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.text.Emails;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Solicitudes de ejercicio de derechos (PRV-05): se reciben desde el sitio con un código de seguimiento y un
 * plazo de respuesta; el encargado de privacidad las atiende desde su bandeja, con aviso por correo en cada paso.
 */
@Service
public class DataSubjectRequestService {

    static final Set<DataSubjectRequestStatus> OPEN = EnumSet.of(DataSubjectRequestStatus.RECEIVED,
            DataSubjectRequestStatus.VERIFYING_IDENTITY, DataSubjectRequestStatus.IN_PROGRESS);
    static final Set<DataSubjectRequestStatus> CLOSED = EnumSet.complementOf(EnumSet.copyOf(OPEN));

    private final DataSubjectRequestRepository requests;
    private final ConsentService consents;
    private final UserAccountRepository users;
    private final PrivacyStaff staff;
    private final SchoolRepository schools;
    private final SchoolTime time;
    private final Formats formats;
    private final PrivacyProperties properties;
    private final AppProperties app;
    private final BlindIndex index;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;

    DataSubjectRequestService(DataSubjectRequestRepository requests, ConsentService consents, UserAccountRepository users,
                              PrivacyStaff staff, SchoolRepository schools, SchoolTime time, Formats formats, PrivacyProperties properties,
                              AppProperties app, BlindIndex index, AuditTrail audit, ApplicationEventPublisher events) {
        this.requests = requests;
        this.consents = consents;
        this.users = users;
        this.staff = staff;
        this.schools = schools;
        this.time = time;
        this.formats = formats;
        this.properties = properties;
        this.app = app;
        this.index = index;
        this.audit = audit;
        this.events = events;
    }

    /** Lo que llega desde el formulario público. */
    public record Submission(DataSubjectRight right, String name, String email, boolean onBehalfOfMinor,
                             String details, boolean noticeRead) {
    }

    @Transactional
    public DataSubjectRequest submit(Submission form, RequestOrigin origin) {
        if (form.right() == null) {
            throw new RuleViolation("Elige qué derecho quieres ejercer");
        }
        String email = form.email() == null ? "" : form.email().strip();
        if (!Emails.isValid(email)) {
            throw new RuleViolation("Escribe un correo válido: ahí te enviaremos la respuesta");
        }
        String name = form.name() == null || form.name().isBlank() ? null : form.name().strip();
        if (name == null || name.length() > 150) {
            throw new RuleViolation("Escribe tu nombre (hasta 150 caracteres)");
        }
        String details = form.details() == null || form.details().isBlank() ? null : form.details().strip();
        if (details != null && details.length() > 5000) {
            throw new RuleViolation("El detalle puede tener hasta 5.000 caracteres");
        }
        if (!form.noticeRead()) {
            throw new RuleViolation("Confirma que leíste el aviso de privacidad de este formulario");
        }
        DataSubject subject = new DataSubject(name, email);
        consents.record(subject, ConsentPurpose.DATA_REQUEST, true, origin);
        LocalDate dueOn = time.today().plusDays(properties.responseDays());
        DataSubjectRequest request = requests.save(new DataSubjectRequest(form.right(), subject, form.onBehalfOfMinor(),
                details, dueOn, index));
        audit.recordAnonymous(AuditAction.CREATE, "DataSubjectRequest", request.getId(), request.getTrackingCode());

        String school = schoolName();
        events.publishEvent(new OutgoingMail(email, "Recibimos tu solicitud " + request.getTrackingCode(), """
                Hola %s:

                %s recibió tu solicitud de %s. Su código es %s.
                Responderemos a más tardar el %s. Si necesitamos acreditar tu identidad (o la del estudiante que
                representas), te escribiremos a este correo.

                Puedes consultar el estado en %s

                Si no hiciste esta solicitud, ignora este correo.
                """.formatted(name, school, rightLabel(form.right()), request.getTrackingCode(), formats.date(dueOn),
                app.url("/privacidad/derechos/estado?codigo=" + request.getTrackingCode()))));
        for (String to : staff.emails()) {
            events.publishEvent(new OutgoingMail(to, "Nueva solicitud de derechos " + request.getTrackingCode(), """
                    Llegó una solicitud de %s. Vence el %s.

                    Atiéndela en %s
                    """.formatted(rightLabel(form.right()), formats.date(dueOn),
                    app.url("/admin/privacy/requests/" + request.getId()))));
        }
        return request;
    }

    /** Consulta pública por código: solo estado y fechas, nunca los datos de la solicitud. */
    @Transactional(readOnly = true)
    public Optional<DataSubjectRequest> byTrackingCode(String code) {
        if (code == null || code.isBlank() || code.length() > 20) {
            return Optional.empty();
        }
        return requests.findByTrackingCode(code.strip().toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<DataSubjectRequest> open() {
        return requests.findByStatusInOrderByDueOnAsc(OPEN);
    }

    @Transactional(readOnly = true)
    public List<DataSubjectRequest> recentlyClosed() {
        return requests.findTop50ByStatusInOrderByResolvedAtDesc(CLOSED);
    }

    @Transactional(readOnly = true)
    public long overdueCount() {
        LocalDate today = time.today();
        return open().stream().filter(r -> r.isOverdue(today)).count();
    }

    /** El encargado abre una solicitud: queda registrado que vio datos personales. */
    @Transactional
    public DataSubjectRequest view(long id) {
        DataSubjectRequest request = requests.findWithHandlerById(id).orElseThrow(() -> new NotFound("La solicitud no existe"));
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "DataSubjectRequest", id, request.getTrackingCode());
        return request;
    }

    @Transactional
    public void requestIdentity(long id, String message) {
        DataSubjectRequest request = get(id);
        String text = required(message, "Explica qué documento o dato necesitas para acreditar la identidad");
        change(() -> request.requestIdentityVerification());
        audit.record(AuditAction.UPDATE, "DataSubjectRequest", id, "Se pide acreditar identidad");
        mail(request, "Necesitamos acreditar tu identidad", """
                Para responder tu solicitud necesitamos confirmar tu identidad:

                %s

                Responde a este correo o acércate al colegio. El plazo sigue corriendo desde la fecha de tu solicitud.
                """.formatted(text));
    }

    @Transactional
    public void start(long id, long userId) {
        DataSubjectRequest request = get(id);
        UserAccount handler = users.findById(userId).orElseThrow();
        change(() -> request.startProcessing(handler));
        audit.record(AuditAction.UPDATE, "DataSubjectRequest", id, "En proceso");
    }

    @Transactional
    public void complete(long id, String resolution) {
        DataSubjectRequest request = get(id);
        String text = required(resolution, "Describe qué se hizo: es lo que recibirá la persona");
        change(() -> request.complete(text));
        audit.record(AuditAction.UPDATE, "DataSubjectRequest", id, "Respondida");
        mail(request, "Respondimos tu solicitud", text);
    }

    @Transactional
    public void reject(long id, String reason) {
        DataSubjectRequest request = get(id);
        String text = required(reason, "El rechazo debe ir fundamentado");
        change(() -> request.reject(text));
        audit.record(AuditAction.REJECT, "DataSubjectRequest", id, "Rechazada");
        mail(request, "Sobre tu solicitud", text + """


                Si no estás de acuerdo con esta respuesta, puedes reclamar ante la Agencia de Protección de Datos Personales.
                """);
    }

    public static String rightLabel(DataSubjectRight right) {
        return switch (right) {
            case ACCESS -> "acceso a tus datos";
            case RECTIFICATION -> "rectificación";
            case ERASURE -> "supresión";
            case OBJECTION -> "oposición";
            case PORTABILITY -> "portabilidad";
            case BLOCKING -> "bloqueo temporal";
        };
    }

    private DataSubjectRequest get(long id) {
        return requests.findById(id).orElseThrow(() -> new NotFound("La solicitud no existe"));
    }

    private void mail(DataSubjectRequest request, String subject, String body) {
        events.publishEvent(new OutgoingMail(request.getRequesterEmail(), subject + " (" + request.getTrackingCode() + ")",
                body + "\n\n" + schoolName()));
    }

    private String schoolName() {
        return schools.findSingleton().map(School::getName).orElse("El colegio");
    }

    private static String required(String text, String problem) {
        if (text == null || text.isBlank()) {
            throw new RuleViolation(problem);
        }
        if (text.strip().length() > 2000) {
            throw new RuleViolation("El texto puede tener hasta 2.000 caracteres");
        }
        return text.strip();
    }

    private static void change(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException e) {
            throw new RuleViolation(e.getMessage());
        }
    }
}
