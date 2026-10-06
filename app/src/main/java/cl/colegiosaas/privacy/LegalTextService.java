package cl.colegiosaas.privacy;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.shared.html.HtmlSanitizer;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Textos legales versionados (DOC-06, PRV-01). Se edita un borrador (desde plantilla o desde la versión
 * vigente) y al publicarlo queda inmutable: cada consentimiento apunta a la versión exacta que se aceptó
 * (PRV-04), y las versiones anteriores siguen consultables.
 */
@Service
public class LegalTextService {

    private final LegalTextRepository texts;
    private final RetentionPolicyRepository retention;
    private final SchoolRepository schools;
    private final UserAccountRepository users;
    private final AuditTrail audit;

    LegalTextService(LegalTextRepository texts, RetentionPolicyRepository retention, SchoolRepository schools,
                     UserAccountRepository users, AuditTrail audit) {
        this.texts = texts;
        this.retention = retention;
        this.schools = schools;
        this.users = users;
        this.audit = audit;
    }

    /** Estado de cada tipo de texto: la versión vigente y el borrador en curso, si hay. */
    public record KindOverview(LegalTextKind kind, LegalText current, LegalText draft) {
    }

    @Transactional(readOnly = true)
    public List<KindOverview> overview() {
        return Arrays.stream(LegalTextKind.values())
                .map(kind -> new KindOverview(kind, current(kind).orElse(null), draft(kind).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<LegalText> current(LegalTextKind kind) {
        return texts.findFirstByKindAndEffectiveFromIsNotNullOrderByVersionNumberDesc(kind);
    }

    /** Versiones publicadas, la más reciente primero. */
    @Transactional(readOnly = true)
    public List<LegalText> history(LegalTextKind kind) {
        return texts.findByKindAndEffectiveFromIsNotNullOrderByVersionNumberDesc(kind);
    }

    @Transactional(readOnly = true)
    public Optional<LegalText> published(LegalTextKind kind, int version) {
        return texts.findByKindAndVersionNumber(kind, version).filter(LegalText::isPublished);
    }

    @Transactional(readOnly = true)
    public LegalText get(long id) {
        return texts.findById(id).orElseThrow(() -> new NotFound("El texto no existe"));
    }

    /**
     * Abre el borrador de la versión siguiente. Parte de la versión vigente si existe (para ajustar), o de
     * la plantilla con los datos del colegio. Si ya hay un borrador, se sigue con ese.
     */
    @Transactional
    public LegalText openDraft(LegalTextKind kind, boolean fromTemplate) {
        Optional<LegalText> existing = draft(kind);
        if (existing.isPresent()) {
            return existing.get();
        }
        int next = texts.findFirstByKindOrderByVersionNumberDesc(kind).map(t -> t.getVersionNumber() + 1).orElse(1);
        Optional<LegalText> current = current(kind);
        LegalText draft = current.isPresent() && !fromTemplate
                ? new LegalText(kind, next, current.get().getTitle(), current.get().getContent())
                : new LegalText(kind, next, LegalTemplates.title(kind), LegalTemplates.content(kind, schoolInfo(kind)));
        texts.save(draft);
        audit.record(AuditAction.CREATE, "LegalText", draft.getId(), kind + " v" + next);
        return draft;
    }

    @Transactional
    public void edit(long id, String title, String content) {
        LegalText text = get(id);
        if (title == null || title.isBlank() || title.strip().length() > 200) {
            throw new RuleViolation("El texto necesita un título de hasta 200 caracteres");
        }
        String clean = HtmlSanitizer.richText(content);
        if (clean.isBlank()) {
            throw new RuleViolation("El texto no puede quedar vacío");
        }
        try {
            text.edit(title.strip(), clean);
        } catch (IllegalStateException e) {
            throw new RuleViolation(e.getMessage());
        }
        audit.record(AuditAction.UPDATE, "LegalText", id, text.getKind() + " v" + text.getVersionNumber());
    }

    /** Publica el borrador: desde ahora los formularios piden consentimiento sobre esta versión. */
    @Transactional
    public void publish(long id, long userId) {
        LegalText text = get(id);
        UserAccount user = users.findById(userId).orElseThrow();
        try {
            text.publish(user);
        } catch (IllegalStateException e) {
            throw new RuleViolation(e.getMessage());
        }
        audit.record(AuditAction.PUBLISH, "LegalText", id, text.getKind() + " v" + text.getVersionNumber());
    }

    @Transactional
    public void discard(long id) {
        LegalText text = get(id);
        if (text.isPublished()) {
            throw new RuleViolation("Una versión publicada no se elimina: es evidencia de lo que se aceptó");
        }
        texts.delete(text);
    }

    /** Publica la primera versión desde la plantilla (lo usan los asistentes cuando falta un texto). */
    @Transactional
    public LegalText publishFromTemplate(LegalTextKind kind, long userId) {
        if (current(kind).isPresent()) {
            throw new RuleViolation("Ya hay una versión publicada de " + LegalTemplates.title(kind));
        }
        LegalText draft = openDraft(kind, true);
        publish(draft.getId(), userId);
        return draft;
    }

    private Optional<LegalText> draft(LegalTextKind kind) {
        return texts.findFirstByKindOrderByVersionNumberDesc(kind).filter(t -> !t.isPublished());
    }

    private LegalTemplates.SchoolInfo schoolInfo(LegalTextKind kind) {
        School school = schools.findSingleton().orElse(null);
        Integer days = retentionCategory(kind).flatMap(retention::findByDataCategory).map(RetentionPolicy::getRetentionDays).orElse(null);
        if (school == null) {
            return new LegalTemplates.SchoolInfo("el colegio", null, null, null, days);
        }
        String address = school.getAddress() == null || school.getAddress().street() == null ? null
                : school.getAddress().street() + (school.getAddress().locality().isEmpty() ? "" : ", " + school.getAddress().locality());
        return new LegalTemplates.SchoolInfo(school.getName(), school.getRbd(), address, school.getContactEmail(), days);
    }

    /** Qué plazo de conservación cita cada aviso. */
    static Optional<RetentionCategory> retentionCategory(LegalTextKind kind) {
        return Optional.ofNullable(switch (kind) {
            case NOTICE_CONTACT -> RetentionCategory.INQUIRIES;
            case NOTICE_SCHEDULING -> RetentionCategory.APPOINTMENTS;
            case NOTICE_EVENTS -> RetentionCategory.EVENT_REGISTRATIONS;
            case NOTICE_ADMISSIONS -> RetentionCategory.PROSPECTS;
            case NOTICE_DATA_REQUESTS -> RetentionCategory.DATA_SUBJECT_REQUESTS;
            default -> null;
        });
    }
}
