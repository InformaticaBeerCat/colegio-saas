package cl.colegiosaas.publicsite;

import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vista previa (CFG-08): el sitio con el diseño en borrador y el borrador de cada página, aunque no
 * esté publicada. Vive bajo {@code /admin}, así que exige sesión, y nunca se indexa.
 */
@Controller
@PreAuthorize("hasAnyAuthority('PAGES', 'SITE_DESIGN')")
class PreviewController {

    private final PageService pages;
    private final SiteContextService sites;
    private final BlockRenderer renderer;

    PreviewController(PageService pages, SiteContextService sites, BlockRenderer renderer) {
        this.pages = pages;
        this.sites = sites;
        this.renderer = renderer;
    }

    @GetMapping(SiteContextService.PREVIEW_ROOT)
    String home(Model model) {
        Page home = pages.home().orElse(null);
        if (home == null) {
            model.addAttribute("site", sites.preview(null));
            return "public/coming-soon";
        }
        return PageViews.render(home, home.getDraftBlocks(), sites.preview(home.getId()), renderer, model);
    }

    @GetMapping(SiteContextService.PREVIEW_ROOT + "/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}")
    String page(@PathVariable String slug, Model model) {
        Page page = pages.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return PageViews.render(page, page.getDraftBlocks(), sites.preview(page.getId()), renderer, model);
    }
}
