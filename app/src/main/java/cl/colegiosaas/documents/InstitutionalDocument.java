package cl.colegiosaas.documents;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Documento institucional obligatorio o relevante (DOC-01): Reglamento Interno, PISE, PEI…
 * Cada publicación crea una versión nueva; la anterior queda archivada, nunca se borra (DOC-03).
 */
@Entity
@Table(name = "institutional_document")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstitutionalDocument extends BaseEntity {

    /** Un documento con más de 12 meses sin actualizar genera alerta al admin (DOC-04). */
    public static final int MONTHS_UNTIL_REVIEW = 12;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private DocumentCategory category;

    @NotBlank
    private String title;

    @NotBlank
    @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
    private String slug;

    @Size(max = 1000)
    private String description;

    /** Protocolos y anexos cuelgan del Reglamento Interno. */
    @ManyToOne(fetch = FetchType.LAZY)
    private InstitutionalDocument parent;

    private int sortOrder;

    /** La más reciente primero. */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL)
    @OrderBy("id DESC")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<DocumentVersion> versions = new ArrayList<>();

    public InstitutionalDocument(DocumentCategory category, String title, String slug) {
        this.category = Objects.requireNonNull(category, "category");
        this.title = title;
        this.slug = slug;
    }

    /**
     * Publica una versión nueva y archiva la vigente. Para el Reglamento Interno y sus partes
     * exige los datos de la REX 781 (DOC-02) y copia nombre y RBD del colegio como evidencia.
     */
    public DocumentVersion publishVersion(VersionDetails details, School school, UserAccount publishedBy) {
        Objects.requireNonNull(details.file(), "file");
        Objects.requireNonNull(details.lastUpdatedOn(), "lastUpdatedOn");
        if (category.requiresRegulatoryMetadata()) {
            if (details.academicYear() == null) {
                throw new IllegalArgumentException("El Reglamento Interno debe indicar el año académico");
            }
            if (school.getRbd() == null) {
                throw new IllegalStateException("El perfil del colegio no tiene RBD; es obligatorio para publicar el Reglamento Interno");
            }
        }
        versions.forEach(DocumentVersion::archive);
        DocumentVersion version = new DocumentVersion(this, details, school.getName(), school.getRbd(), publishedBy);
        versions.addFirst(version);
        return version;
    }

    public Optional<DocumentVersion> currentVersion() {
        return versions.stream().filter(DocumentVersion::isCurrentVersion).findFirst();
    }

    public List<DocumentVersion> archivedVersions() {
        return versions.stream().filter(v -> !v.isCurrentVersion()).toList();
    }

    /** DOC-04: verdadero si la versión vigente cumplió 12 meses sin actualizarse. */
    public boolean needsAnnualReview(LocalDate today) {
        return currentVersion()
                .map(v -> !v.getLastUpdatedOn().plusMonths(MONTHS_UNTIL_REVIEW).isAfter(today))
                .orElse(false);
    }

    /** Datos que entrega quien sube la versión. */
    public record VersionDetails(
            StoredFile file,
            Integer academicYear,
            LocalDate lastUpdatedOn,
            boolean accessiblePdf,
            String changeNotes) {
    }
}
