package cl.colegiosaas.publicsite;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Página de resultados de la búsqueda interna (UX-06). No se indexa: los buscadores ya tienen el sitemap. */
@Controller
class SearchController {

    private final SiteSearch search;
    private final PublicPages pages;

    SearchController(SiteSearch search, PublicPages pages) {
        this.search = search;
        this.pages = pages;
    }

    @GetMapping("/buscar")
    String search(@RequestParam(name = "q", required = false) String query, Model model) {
        pages.prepare(model, query == null || query.isBlank() ? "Buscar" : "Buscar: " + query.strip(), null);
        model.addAttribute("noindex", true);
        model.addAttribute("query", query == null ? "" : query.strip());
        model.addAttribute("results", search.search(query));
        model.addAttribute("searched", query != null && !query.isBlank());
        return "public/search";
    }
}
