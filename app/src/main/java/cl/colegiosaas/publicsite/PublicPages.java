package cl.colegiosaas.publicsite;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Prepara el modelo común de las páginas de módulos (noticias, calendario, documentos…): el contexto
 * del sitio, el título de la pestaña y la descripción para buscadores.
 */
@Component
public class PublicPages {

    private final SiteContextService sites;

    PublicPages(SiteContextService sites) {
        this.sites = sites;
    }

    public void prepare(Model model, String title, String description) {
        SiteContext site = sites.publicSite(null);
        model.addAttribute("site", site);
        model.addAttribute("pageTitle", title + " · " + site.schoolName());
        model.addAttribute("metaDescription", description);
        model.addAttribute("noindex", false);
    }
}
