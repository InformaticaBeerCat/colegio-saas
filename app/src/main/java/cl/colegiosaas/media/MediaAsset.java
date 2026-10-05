package cl.colegiosaas.media;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Elemento de la biblioteca de medios (MED-01): foto, video o documento.
 * Una misma foto puede usarse en álbumes, noticias y bloques; su revisión y su retiro valen para todos.
 */
@Entity
@Table(name = "media_asset")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset extends BaseEntity {

    private static final Pattern EMBED_HOSTS = Pattern.compile(
            "https://(www\\.youtube\\.com|youtu\\.be|vimeo\\.com|player\\.vimeo\\.com)/.+");

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private MediaKind kind;

    /** Nulo solo en videos embebidos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private StoredFile file;

    @Setter(AccessLevel.NONE)
    private String embedUrl;

    /** Texto alternativo (ACC-02). Obligatorio en imágenes para poder aprobarlas. */
    @Size(max = 300)
    private String altText;

    /** Pie de foto. MED-12: nunca nombre completo de un estudiante (se valida en el servicio). */
    @Size(max = 500)
    private String caption;

    /** Créditos de la imagen (Ley 17.336). */
    private String credits;

    @ManyToOne(fetch = FetchType.LAZY)
    private MediaFolder folder;

    @ManyToMany
    @JoinTable(name = "media_asset_tag",
            joinColumns = @JoinColumn(name = "media_asset_id"),
            inverseJoinColumns = @JoinColumn(name = "media_tag_id"))
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Set<MediaTag> tags = new HashSet<>();

    private LocalDate takenOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private UserAccount uploadedBy;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private ReviewStatus reviewStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private UserAccount reviewedBy;

    @Setter(AccessLevel.NONE)
    private Instant reviewedAt;

    @Setter(AccessLevel.NONE)
    private String reviewNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Setter(AccessLevel.NONE)
    private List<BlurRegion> blurRegions = List.of();

    /** Versión con las zonas difuminadas; es la que se publica si existe. */
    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private StoredFile blurredFile;

    @Setter(AccessLevel.NONE)
    private Instant withdrawnAt;

    @Setter(AccessLevel.NONE)
    private String withdrawalReason;

    private MediaAsset(MediaKind kind, StoredFile file, String embedUrl, UserAccount uploadedBy) {
        this.kind = kind;
        this.file = file;
        this.embedUrl = embedUrl;
        this.uploadedBy = uploadedBy;
        this.reviewStatus = kind.requiresReview() ? ReviewStatus.PENDING_REVIEW : ReviewStatus.NOT_REQUIRED;
    }

    public static MediaAsset upload(MediaKind kind, StoredFile file, UserAccount uploadedBy) {
        if (kind == MediaKind.EMBEDDED_VIDEO) {
            throw new IllegalArgumentException("Un video embebido se crea con embed()");
        }
        return new MediaAsset(kind, Objects.requireNonNull(file, "file"), null, uploadedBy);
    }

    public static MediaAsset embed(String url, UserAccount uploadedBy) {
        if (url == null || !EMBED_HOSTS.matcher(url).matches()) {
            throw new IllegalArgumentException("Solo se aceptan videos de YouTube o Vimeo: " + url);
        }
        return new MediaAsset(MediaKind.EMBEDDED_VIDEO, null, url, uploadedBy);
    }

    // --- Revisión de autorización de imagen (MED-06) ---

    public void approve(UserAccount reviewer) {
        if (kind == MediaKind.IMAGE && (altText == null || altText.isBlank())) {
            throw new IllegalStateException("La imagen necesita texto alternativo antes de aprobarse");
        }
        recordReview(ReviewStatus.APPROVED, reviewer, null);
    }

    public void reject(UserAccount reviewer, String note) {
        recordReview(ReviewStatus.REJECTED, reviewer, note);
    }

    /** Para imágenes sin personas (logo, fachada): el gestor indica que no necesitan autorización. */
    public void exemptFromReview(UserAccount reviewer) {
        recordReview(ReviewStatus.NOT_REQUIRED, reviewer, null);
    }

    private void recordReview(ReviewStatus status, UserAccount reviewer, String note) {
        reviewStatus = status;
        reviewedBy = Objects.requireNonNull(reviewer, "reviewer");
        reviewedAt = Instant.now();
        reviewNote = note;
    }

    // --- Difuminado (MED-07) ---

    /** {@code rendered} es la imagen ya procesada con las zonas difuminadas (la genera la fase 5). */
    public void applyBlur(List<BlurRegion> regions, StoredFile rendered) {
        blurRegions = List.copyOf(regions);
        blurredFile = regions.isEmpty() ? null : Objects.requireNonNull(rendered, "rendered");
    }

    /** Archivo que ven los visitantes: el difuminado si existe. */
    public StoredFile publicFile() {
        return blurredFile != null ? blurredFile : file;
    }

    // --- Retiro (MED-09) ---

    /** Saca el medio de todo el sitio de inmediato (p. ej., ante la revocación de una autorización). */
    public void withdraw(String reason) {
        withdrawnAt = Instant.now();
        withdrawalReason = reason;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /** La única regla que consulta todo lo que muestra medios: álbumes, noticias, bloques. */
    public boolean isDisplayable() {
        return !isWithdrawn()
                && (reviewStatus == ReviewStatus.APPROVED || reviewStatus == ReviewStatus.NOT_REQUIRED);
    }

    // --- Etiquetas ---

    public void addTag(MediaTag tag) {
        tags.add(tag);
    }

    public void removeTag(MediaTag tag) {
        tags.remove(tag);
    }

    public Set<MediaTag> getTags() {
        return Collections.unmodifiableSet(tags);
    }
}
