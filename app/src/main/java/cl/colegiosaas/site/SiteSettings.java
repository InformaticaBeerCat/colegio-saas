package cl.colegiosaas.site;

import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.shared.persistence.SingletonEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Configuración visual y de contacto del sitio (una fila por instalación).
 * El diseño tiene dos versiones: el borrador se edita y previsualiza (CFG-08) y recién
 * al publicarlo lo ven los visitantes.
 */
@Entity
@Table(name = "site_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteSettings extends SingletonEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Setter(AccessLevel.NONE)
    private SiteDesign publishedDesign;

    @JdbcTypeCode(SqlTypes.JSON)
    @Setter(AccessLevel.NONE)
    private SiteDesign draftDesign;

    @JdbcTypeCode(SqlTypes.JSON)
    @Setter(AccessLevel.NONE)
    private List<SocialLink> socialLinks = List.of();

    /** Número para el botón click-to-chat (COM-03), formato internacional: +56912345678. */
    @Pattern(regexp = "\\+\\d{8,15}")
    private String whatsappNumber;

    private String footerText;

    @ManyToOne(fetch = FetchType.LAZY)
    private MediaAsset logo;

    @ManyToOne(fetch = FetchType.LAZY)
    private MediaAsset favicon;

    public SiteSettings(SiteDesign initialDesign) {
        this.publishedDesign = initialDesign;
        this.draftDesign = initialDesign;
    }

    public void updateDraft(SiteDesign design) {
        draftDesign = design;
    }

    public void publishDraft() {
        publishedDesign = draftDesign;
    }

    public void discardDraft() {
        draftDesign = publishedDesign;
    }

    public boolean hasUnpublishedChanges() {
        return !draftDesign.equals(publishedDesign);
    }

    public void replaceSocialLinks(List<SocialLink> links) {
        socialLinks = List.copyOf(links);
    }
}
