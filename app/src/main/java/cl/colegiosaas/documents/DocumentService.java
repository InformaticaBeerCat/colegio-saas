package cl.colegiosaas.documents;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.text.Slugs;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Documentos institucionales (DOC-01) con historial de versiones (DOC-03). El Reglamento Interno,
 * sus protocolos y anexos llevan año académico, nombre y RBD del colegio (REX 781, DOC-02).
 */
@Service
public class DocumentService {

    /** Las partes del Reglamento Interno cuelgan de él. */
    private static final Set<DocumentCategory> CHILD_CATEGORIES = Set.of(DocumentCategory.PROTOCOL, DocumentCategory.ANNEX);

    private final InstitutionalDocumentRepository documents;
    private final SchoolRepository schools;
    private final UserAccountRepository users;
    private final SchoolTime time;
    private final AuditTrail audit;

    DocumentService(InstitutionalDocumentRepository documents, SchoolRepository schools, UserAccountRepository users,
                    SchoolTime time, AuditTrail audit) {
        this.documents = documents;
        this.schools = schools;
        this.users = users;
        this.time = time;
        this.audit = audit;
    }

    /** En el orden de {@link DocumentCategory} (el Reglamento Interno primero), luego por orden y título. */
    @Transactional(readOnly = true)
    public List<InstitutionalDocument> list() {
        return documents.findAllByOrderByCategoryAscSortOrderAsc().stream()
                .sorted(Comparator.comparing(InstitutionalDocument::getCategory)
                        .thenComparingInt(InstitutionalDocument::getSortOrder)
                        .thenComparing(InstitutionalDocument::getTitle))
                .toList();
    }

    @Transactional(readOnly = true)
    public InstitutionalDocument get(long id) {
        return documents.findWithVersionsById(id).orElseThrow(() -> new NotFound("El documento no existe"));
    }

    /** Posibles padres de un protocolo o anexo: los Reglamentos Internos. */
    @Transactional(readOnly = true)
    public List<InstitutionalDocument> regulations() {
        return list().stream().filter(d -> d.getCategory() == DocumentCategory.INTERNAL_REGULATIONS).toList();
    }

    /** DOC-04: documentos con versión vigente de hace 12 meses o más. */
    @Transactional(readOnly = true)
    public List<InstitutionalDocument> dueForReview() {
        LocalDate today = time.today();
        return list().stream().filter(d -> d.needsAnnualReview(today)).toList();
    }

    @Transactional
    public InstitutionalDocument create(DocumentCategory category, String title, String description, Long parentId) {
        requireTitle(title);
        String slug = Slugs.unique(title, "documento", s -> documents.findBySlug(s).isPresent());
        InstitutionalDocument document = new InstitutionalDocument(category, title.strip(), slug);
        applyDetails(document, title, description, parentId);
        documents.save(document);
        audit.record(AuditAction.CREATE, "InstitutionalDocument", document.getId(), document.getTitle());
        return document;
    }

    @Transactional
    public void update(long id, String title, String description, Long parentId, int sortOrder) {
        InstitutionalDocument document = get(id);
        requireTitle(title);
        applyDetails(document, title, description, parentId);
        document.setSortOrder(sortOrder);
        audit.record(AuditAction.UPDATE, "InstitutionalDocument", id, document.getTitle());
    }

    /**
     * Publica una versión nueva: la anterior queda archivada y sigue disponible en el historial.
     * La fecha de última actualización no puede estar en el futuro.
     */
    @Transactional
    public DocumentVersion publishVersion(long id, StoredFile file, Integer academicYear, LocalDate lastUpdatedOn,
                                          boolean accessiblePdf, String changeNotes, long userId) {
        InstitutionalDocument document = get(id);
        if (lastUpdatedOn == null) {
            throw new RuleViolation("Indica la fecha de última actualización del documento");
        }
        if (lastUpdatedOn.isAfter(time.today())) {
            throw new RuleViolation("La fecha de última actualización no puede ser futura");
        }
        if (changeNotes != null && changeNotes.length() > 1000) {
            throw new RuleViolation("Las notas de cambio admiten hasta 1000 caracteres");
        }
        School school = schools.findSingleton().orElseThrow();
        UserAccount publisher = users.findById(userId).orElseThrow();
        DocumentVersion version;
        try {
            version = document.publishVersion(new InstitutionalDocument.VersionDetails(
                    file, academicYear, lastUpdatedOn, accessiblePdf, blankToNull(changeNotes)), school, publisher);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new RuleViolation(e.getMessage());
        }
        audit.record(AuditAction.PUBLISH, "InstitutionalDocument", id,
                document.getTitle() + (academicYear == null ? "" : " (" + academicYear + ")"));
        return version;
    }

    /** Un documento publicado nunca se borra: su historial es evidencia ante la Superintendencia. */
    @Transactional
    public void delete(long id) {
        InstitutionalDocument document = get(id);
        if (document.currentVersion().isPresent() || !document.archivedVersions().isEmpty()) {
            throw new RuleViolation("El documento tiene versiones publicadas; su historial se conserva y no se puede eliminar");
        }
        if (documents.existsByParent(document)) {
            throw new RuleViolation("El documento tiene protocolos o anexos asociados");
        }
        documents.delete(document);
        audit.record(AuditAction.DELETE, "InstitutionalDocument", id, document.getTitle());
    }

    // --- Sitio público ---

    /** Documentos con versión vigente, en el orden de la página. */
    @Transactional(readOnly = true)
    public List<InstitutionalDocument> published() {
        return list().stream().filter(d -> d.currentVersion().isPresent()).toList();
    }

    @Transactional(readOnly = true)
    public InstitutionalDocument publishedBySlug(String slug) {
        return documents.findBySlug(slug).filter(d -> d.currentVersion().isPresent())
                .orElseThrow(() -> new NotFound("El documento no existe"));
    }

    private void applyDetails(InstitutionalDocument document, String title, String description, Long parentId) {
        document.setTitle(title.strip());
        if (description != null && description.length() > 1000) {
            throw new RuleViolation("La descripción admite hasta 1000 caracteres");
        }
        document.setDescription(blankToNull(description));
        if (parentId == null) {
            document.setParent(null);
            return;
        }
        if (!CHILD_CATEGORIES.contains(document.getCategory())) {
            throw new RuleViolation("Solo los protocolos y anexos van dentro del Reglamento Interno");
        }
        InstitutionalDocument parent = get(parentId);
        if (parent.getCategory() != DocumentCategory.INTERNAL_REGULATIONS) {
            throw new RuleViolation("Los protocolos y anexos van dentro de un Reglamento Interno");
        }
        document.setParent(parent);
    }

    private static void requireTitle(String title) {
        if (title == null || title.isBlank() || title.strip().length() > 200) {
            throw new RuleViolation("El documento necesita un título de hasta 200 caracteres");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
