package cl.colegiosaas.publicsite;

import cl.colegiosaas.page.MenuEntry;
import cl.colegiosaas.site.FooterSettings;
import cl.colegiosaas.site.SiteAlert;

import java.util.List;

/**
 * Todo lo común a las páginas del sitio público: marca, menús, pie y alertas. Las plantillas lo
 * reciben como {@code site}.
 *
 * @param themeId        clase CSS del tema ({@code theme-classic})
 * @param stylesheetUrl  hoja de tokens con su versión, publicada o de vista previa
 * @param homeHref       portada: "/" en el sitio, la de vista previa en el panel
 * @param preview        se está viendo el borrador desde el panel (nunca se indexa)
 */
public record SiteContext(
        String schoolName,
        String themeId,
        String stylesheetUrl,
        String homeHref,
        List<MenuEntry> headerMenu,
        List<MenuEntry> footerMenu,
        FooterSettings footer,
        List<SiteAlert> alerts,
        boolean preview) {

    /** Enlace de WhatsApp click-to-chat (COM-03) a partir del número en formato +569…. */
    public String whatsappHref() {
        String number = footer.whatsappNumber();
        return number == null ? null : "https://wa.me/" + number.substring(1);
    }
}
