package cl.colegiosaas.publicsite;

import cl.colegiosaas.news.NewsArticle;
import cl.colegiosaas.news.NewsCategory;
import cl.colegiosaas.news.NewsCategoryService;
import cl.colegiosaas.news.NewsService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.shared.text.Slugs;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Noticias en el sitio (NOT-01): listado con filtros por categoría y nivel, y la página de cada una.
 * Los filtros usan slugs en la URL, nunca ids.
 */
@Controller
@RequiresFeature(Feature.NEWS)
class NewsPublicController {

    static final int PAGE_SIZE = 10;

    private final NewsService news;
    private final NewsCategoryService categories;
    private final GradeLevelRepository gradeLevels;
    private final PublicPages pages;

    NewsPublicController(NewsService news, NewsCategoryService categories, GradeLevelRepository gradeLevels, PublicPages pages) {
        this.news = news;
        this.categories = categories;
        this.gradeLevels = gradeLevels;
        this.pages = pages;
    }

    @GetMapping("/noticias")
    String list(@RequestParam(name = "categoria", required = false) String categorySlug,
                @RequestParam(name = "nivel", required = false) String levelSlug,
                @RequestParam(name = "pagina", defaultValue = "1") int pageNumber, Model model) {
        List<NewsCategory> allCategories = categories.list();
        List<GradeLevel> levels = gradeLevels.findAllByOrderBySortOrderAsc();
        NewsCategory category = allCategories.stream().filter(c -> c.getSlug().equals(categorySlug)).findFirst().orElse(null);
        GradeLevel level = levels.stream().filter(l -> Slugs.slugify(l.getName()).equals(levelSlug)).findFirst().orElse(null);

        Page<NewsArticle> page = news.visible(category == null ? null : category.getId(), level == null ? null : level.getId(),
                PageRequest.of(Math.max(0, pageNumber - 1), PAGE_SIZE));
        pages.prepare(model, "Noticias", "Noticias y actividades del colegio");
        model.addAttribute("articles", page);
        model.addAttribute("categories", allCategories);
        model.addAttribute("levels", levels.stream().map(l -> new LevelOption(Slugs.slugify(l.getName()), l.getName())).toList());
        model.addAttribute("categoria", category == null ? null : category.getSlug());
        model.addAttribute("nivel", level == null ? null : levelSlug);
        return "public/news/list";
    }

    @GetMapping("/noticias/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}")
    String article(@PathVariable String slug, Model model) {
        NewsArticle article = news.visibleBySlug(slug);
        String description = article.getSeo() != null && article.getSeo().metaDescription() != null
                ? article.getSeo().metaDescription() : article.getSummary();
        pages.prepare(model, article.getSeo() != null && article.getSeo().metaTitle() != null
                ? article.getSeo().metaTitle() : article.getTitle(), description);
        model.addAttribute("article", article);
        return "public/news/article";
    }

    /** Opción de filtro: el slug va en la URL, el nombre se muestra. */
    record LevelOption(String slug, String name) {
    }
}
