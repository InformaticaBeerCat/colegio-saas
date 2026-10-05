package cl.colegiosaas.page;

import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.site.SiteSection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * Página armada con bloques (CFG-04). Se edita el borrador; publicar copia el borrador a la
 * versión pública, así los visitantes nunca ven una edición a medias.
 */
@Entity
@Table(name = "page")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Page extends BaseEntity {

    /** Parte de la URL: minúsculas, números y guiones. */
    @NotBlank
    @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
    private String slug;

    @NotBlank
    private String title;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private PageKind kind;

    @Enumerated(EnumType.STRING)
    private SiteSection section = SiteSection.MAIN;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private PageStatus status = PageStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Setter(AccessLevel.NONE)
    private List<Block> draftBlocks = List.of();

    /** Nulo mientras la página nunca se haya publicado. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Setter(AccessLevel.NONE)
    private List<Block> publishedBlocks;

    @Setter(AccessLevel.NONE)
    private Instant publishedAt;

    private SeoMetadata seo;

    /** Páginas privadas o de comunidad no se indexan (SEO-03). */
    private boolean noindex;

    public Page(String slug, String title, PageKind kind) {
        this.slug = slug;
        this.title = title;
        this.kind = kind;
    }

    public void editBlocks(List<Block> blocks) {
        draftBlocks = List.copyOf(blocks);
    }

    public void publish() {
        publishedBlocks = draftBlocks;
        status = PageStatus.PUBLISHED;
        publishedAt = Instant.now();
    }

    /** Saca la página del sitio; conserva la última versión publicada por si se vuelve a publicar. */
    public void unpublish() {
        status = PageStatus.DRAFT;
    }

    public boolean isPublished() {
        return status == PageStatus.PUBLISHED;
    }

    public boolean hasUnpublishedChanges() {
        return !draftBlocks.equals(publishedBlocks);
    }
}
