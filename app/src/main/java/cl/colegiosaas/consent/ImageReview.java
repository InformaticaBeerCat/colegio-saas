package cl.colegiosaas.consent;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaAssetRepository;
import cl.colegiosaas.media.ReviewStatus;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Revisión de fotos por el gestor de consentimientos (MED-06). El gestor marca qué estudiantes aparecen
 * (sin reconocimiento facial) y el sistema revisa sus autorizaciones para el sitio web. Una foto con un
 * estudiante sin autorización solo se aprueba si ese estudiante quedó difuminado (MED-07).
 */
@Service
public class ImageReview {

    private final MediaAssetRepository assets;
    private final StudentRepository students;
    private final StudentAppearanceRepository appearances;
    private final ImageConsentRepository consents;
    private final UserAccountRepository users;
    private final AuditTrail audit;

    ImageReview(MediaAssetRepository assets, StudentRepository students, StudentAppearanceRepository appearances,
                ImageConsentRepository consents, UserAccountRepository users, AuditTrail audit) {
        this.assets = assets;
        this.students = students;
        this.appearances = appearances;
        this.consents = consents;
        this.users = users;
        this.audit = audit;
    }

    /** Estudiante etiquetado en una foto y si tiene autorización vigente para el sitio web. */
    public record Appearance(long studentId, String name, String course, boolean authorized) {
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> queue() {
        return assets.findByReviewStatusOrderByCreatedAtAsc(ReviewStatus.PENDING_REVIEW);
    }

    @Transactional(readOnly = true)
    public List<Appearance> appearancesIn(long assetId) {
        List<Student> tagged = appearances.findStudentsIn(asset(assetId));
        Set<Long> authorized = authorizedIds(tagged);
        return tagged.stream()
                .map(s -> new Appearance(s.getId(), s.getFullName(), s.getCourse().displayName(), authorized.contains(s.getId())))
                .sorted(Comparator.comparing(Appearance::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public void tag(long assetId, Set<Long> studentIds, long reviewerId) {
        MediaAsset asset = asset(assetId);
        UserAccount reviewer = user(reviewerId);
        for (Student student : students.findAllById(studentIds)) {
            if (appearances.findByStudentAndAsset(student, asset).isEmpty()) {
                appearances.save(new StudentAppearance(student, asset, reviewer));
            }
        }
    }

    @Transactional
    public void untag(long assetId, long studentId) {
        MediaAsset asset = asset(assetId);
        students.findById(studentId)
                .flatMap(student -> appearances.findByStudentAndAsset(student, asset))
                .ifPresent(appearances::delete);
    }

    /**
     * Aprueba la foto para el sitio. Si aparece alguien sin autorización vigente, el gestor tiene que
     * haberlo difuminado y confirmarlo; si no, la aprobación se rechaza nombrando a quién falta.
     */
    @Transactional
    public void approve(long assetId, boolean confirmedBlurred, long reviewerId) {
        MediaAsset asset = asset(assetId);
        List<Appearance> missing = appearancesIn(assetId).stream().filter(a -> !a.authorized()).toList();
        if (!missing.isEmpty()) {
            String names = missing.stream().map(Appearance::name).collect(Collectors.joining(", "));
            if (asset.getBlurRegions().isEmpty()) {
                throw new RuleViolation("Sin autorización de imagen para el sitio: " + names
                        + ". Difumínalos o rechaza la foto.");
            }
            if (!confirmedBlurred) {
                throw new RuleViolation("Confirma que quedaron difuminados: " + names);
            }
        }
        try {
            asset.approve(user(reviewerId));
        } catch (IllegalStateException e) {
            throw new RuleViolation("Escribe el texto alternativo de la foto antes de aprobarla (ACC-02)");
        }
        audit.record(AuditAction.APPROVE, "MediaAsset", assetId,
                missing.isEmpty() ? null : "Con " + missing.size() + " estudiante(s) difuminado(s)");
    }

    @Transactional
    public void reject(long assetId, String note, long reviewerId) {
        asset(assetId).reject(user(reviewerId), note == null || note.isBlank() ? null : note.strip());
        audit.record(AuditAction.REJECT, "MediaAsset", assetId, note);
    }

    /** Fotos sin personas (fachada, logo, paisaje): no necesitan autorización. */
    @Transactional
    public void exempt(long assetId, long reviewerId) {
        MediaAsset asset = asset(assetId);
        if (!appearancesIn(assetId).isEmpty()) {
            throw new RuleViolation("La foto tiene estudiantes etiquetados: no se puede marcar como sin personas");
        }
        asset.exemptFromReview(user(reviewerId));
        audit.record(AuditAction.APPROVE, "MediaAsset", assetId, "Sin personas");
    }

    private Set<Long> authorizedIds(List<Student> tagged) {
        return tagged.isEmpty() ? Set.of() : new HashSet<>(consents.studentIdsWithActiveConsent(tagged, ConsentChannel.WEBSITE));
    }

    private MediaAsset asset(long id) {
        return assets.findById(id).orElseThrow(() -> new NotFound("El medio no existe"));
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow(() -> new NotFound("La cuenta no existe"));
    }
}
