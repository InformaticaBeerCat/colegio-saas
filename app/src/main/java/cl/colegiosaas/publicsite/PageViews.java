package cl.colegiosaas.publicsite;

import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import org.springframework.ui.Model;

import java.util.List;

/** Lo que comparten el sitio y la vista previa al mostrar una página. */
final class PageViews {

    private PageViews() {
    }

    static String render(Page page, List<Block> blocks, SiteContext site, BlockRenderer renderer, Seo seo, Model model) {
        model.addAttribute("site", site);
        model.addAttribute("page", page);
        model.addAttribute("blocks", renderer.render(blocks));
        model.addAttribute("pageTitle", pageTitle(page, site));
        model.addAttribute("metaDescription", page.getSeo() == null ? null : page.getSeo().metaDescription());
        // La vista previa nunca se indexa (SEO-03).
        model.addAttribute("noindex", site.preview() || page.isNoindex());
        model.addAttribute("seo", seo);
        return "public/page";
    }

    /** "Admisión · Colegio San José"; en la portada, solo el colegio (o el título SEO si lo hay). */
    private static String pageTitle(Page page, SiteContext site) {
        String custom = page.getSeo() == null ? null : page.getSeo().metaTitle();
        if (custom != null) {
            return custom;
        }
        return page.getKind() == PageKind.HOME ? site.schoolName() : page.getTitle() + " · " + site.schoolName();
    }
}
