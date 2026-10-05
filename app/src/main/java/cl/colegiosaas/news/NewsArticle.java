package cl.colegiosaas.news;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.Album;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.page.SeoMetadata;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.site.SiteSection;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Noticia (NOT-01) con flujo borrador → revisión → publicada o programada (NOT-02).
 * Los métodos de flujo reciben {@code now} para que la programación sea fácil de probar.
 */
@Entity
@Table(name = "news_article")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle extends BaseEntity {

    @NotBlank
    @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
    private String slug;

    @NotBlank
    private String title;

    /** Bajada: aparece en listados y en Open Graph (NOT-06). */
    @Size(max = 500)
    private String summary;

    /** HTML saneado al guardar (fase 4). Columna LONGTEXT. */
    private String body = "";

    @ManyToOne(fetch = FetchType.LAZY)
    private MediaAsset featuredImage;

    /** Video embebido (YouTube/Vimeo) o alojado. */
    @ManyToOne(fetch = FetchType.LAZY)
    private MediaAsset video;

    @ManyToOne(fetch = FetchType.LAZY)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY)
    private NewsCategory category;

    /** Etiquetas por nivel (NOT-01); vacío = noticia para todo el colegio. */
    @ManyToMany
    @JoinTable(name = "news_article_grade_level",
            joinColumns = @JoinColumn(name = "news_article_id"),
            inverseJoinColumns = @JoinColumn(name = "grade_level_id"))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<GradeLevel> gradeLevels = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private SiteSection section = SiteSection.MAIN;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private NewsStatus status = NewsStatus.DRAFT;

    @Setter(AccessLevel.NONE)
    private Instant publishAt;

    @Setter(AccessLevel.NONE)
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Setter(AccessLevel.NONE)
    private UserAccount author;

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private UserAccount reviewer;

    /** Comentario del revisor al devolver a borrador. */
    @Setter(AccessLevel.NONE)
    private String reviewNote;

    private SeoMetadata seo;

    public NewsArticle(String slug, String title, UserAccount author) {
        this.slug = slug;
        this.title = title;
        this.author = Objects.requireNonNull(author, "author");
    }

    // --- Flujo de aprobación (NOT-02) ---

    public void submitForReview() {
        requireStatus(NewsStatus.DRAFT);
        status = NewsStatus.IN_REVIEW;
    }

    /** Aprueba: publica de inmediato o programa si {@code publishAt} es futuro. */
    public void approve(UserAccount approver, Instant publishAt, Instant now) {
        requireStatus(NewsStatus.IN_REVIEW);
        reviewer = Objects.requireNonNull(approver, "approver");
        reviewNote = null;
        if (publishAt != null && publishAt.isAfter(now)) {
            status = NewsStatus.SCHEDULED;
            this.publishAt = publishAt;
        } else {
            status = NewsStatus.PUBLISHED;
            this.publishAt = now;
            publishedAt = now;
        }
    }

    public void returnToDraft(UserAccount approver, String note) {
        if (status != NewsStatus.IN_REVIEW && status != NewsStatus.SCHEDULED) {
            throw new IllegalStateException("Solo se devuelve una noticia en revisión o programada");
        }
        reviewer = Objects.requireNonNull(approver, "approver");
        reviewNote = note;
        status = NewsStatus.DRAFT;
        publishAt = null;
    }

    /** Lo llama la tarea programada (fase 4) para pasar de SCHEDULED a PUBLISHED. */
    public void publishIfDue(Instant now) {
        if (status == NewsStatus.SCHEDULED && !publishAt.isAfter(now)) {
            status = NewsStatus.PUBLISHED;
            publishedAt = publishAt;
        }
    }

    public void archive() {
        status = NewsStatus.ARCHIVED;
    }

    /** Visible aunque la tarea programada aún no haya corrido. */
    public boolean isVisibleAt(Instant now) {
        return status == NewsStatus.PUBLISHED
                || (status == NewsStatus.SCHEDULED && !publishAt.isAfter(now));
    }

    private void requireStatus(NewsStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("La noticia está en " + status + ", se esperaba " + expected);
        }
    }

    // --- Etiquetas por nivel ---

    public void tagGradeLevel(GradeLevel level) {
        gradeLevels.add(level);
    }

    public void untagGradeLevel(GradeLevel level) {
        gradeLevels.remove(level);
    }

    public Set<GradeLevel> getGradeLevels() {
        return Collections.unmodifiableSet(gradeLevels);
    }
}
