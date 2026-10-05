package cl.colegiosaas.publicsite;

import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sitio público armado con bloques (PUB-01). La portada es la página de tipo HOME; mientras no esté
 * publicada se muestra un aviso de sitio en construcción.
 */
@Controller
class PublicPageController {

    private final PageService pages;
    private final SiteContextService sites;
    private final BlockRenderer renderer;

    PublicPageController(PageService pages, SiteContextService sites, BlockRenderer renderer) {
        this.pages = pages;
        this.sites = sites;
        this.renderer = renderer;
    }

    @GetMapping("/")
    String home(Model model) {
        Page home = pages.home().filter(Page::isPublished).orElse(null);
        if (home == null) {
            model.addAttribute("site", sites.publicSite(null));
            return "public/coming-soon";
        }
        return PageViews.render(home, home.getPublishedBlocks(), sites.publicSite(home.getId()), renderer, model);
    }

    /** Un solo tramo de URL; las rutas del sistema ({@code /admin}, {@code /setup}…) tienen sus propios controladores. */
    @GetMapping("/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}")
    String page(@PathVariable String slug, Model model) {
        Page page = pages.findPublished(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (page.getKind() == PageKind.HOME) {
            return "redirect:/";
        }
        return PageViews.render(page, page.getPublishedBlocks(), sites.publicSite(page.getId()), renderer, model);
    }
}
