package cl.colegiosaas.publicsite;

import cl.colegiosaas.shared.web.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Prepara el modelo común de las páginas de módulos (noticias, calendario, documentos…): el contexto
 * del sitio, el título de la pestaña, la descripción para buscadores y los metadatos para compartir (SEO).
 */
@Component
public class PublicPages {

    private final SiteContextService sites;
    private final AppProperties app;

    PublicPages(SiteContextService sites, AppProperties app) {
        this.sites = sites;
        this.app = app;
    }

    public void prepare(Model model, String title, String description) {
        SiteContext site = sites.publicSite(null);
        model.addAttribute("site", site);
        model.addAttribute("pageTitle", title + " · " + site.schoolName());
        model.addAttribute("metaDescription", description);
        model.addAttribute("noindex", false);
        model.addAttribute("seo", seoFor(site));
    }

    /** Metadatos por defecto: la URL canónica de la petición en curso y el logo del colegio como imagen. */
    public Seo seoFor(SiteContext site) {
        String path = "/";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            path = request.getRequestURI();
        }
        String logo = site.logo() == null ? null : absolute(site.logo().src());
        return new Seo(app.url(path), "website", logo, null);
    }

    /** Metadatos que ya preparó {@link #prepare}, para que el controlador los complete. */
    public Seo seo(Model model) {
        return (Seo) model.getAttribute("seo");
    }

    public String absolute(String path) {
        return path == null || path.startsWith("http") ? path : app.url(path);
    }
}
