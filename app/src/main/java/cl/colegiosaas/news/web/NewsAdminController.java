package cl.colegiosaas.news.web;

import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.news.NewsArticle;
import cl.colegiosaas.news.NewsCategoryService;
import cl.colegiosaas.news.NewsService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.site.SiteSection;
import cl.colegiosaas.structure.GradeLevelRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/** Noticias en el panel (NOT-01, NOT-02). */
@Controller
@RequestMapping("/admin/news")
@RequiresFeature(Feature.NEWS)
@PreAuthorize("hasAuthority('NEWS_EDIT')")
class NewsAdminController {

    private final NewsService news;
    private final NewsCategoryService categories;
    private final GradeLevelRepository gradeLevels;
    private final SchoolTime time;
    private final MediaLibrary media;

    NewsAdminController(NewsService news, NewsCategoryService categories, GradeLevelRepository gradeLevels, SchoolTime time,
                        MediaLibrary media) {
        this.news = news;
        this.categories = categories;
        this.gradeLevels = gradeLevels;
        this.time = time;
        this.media = media;
    }

    @GetMapping
    String list(@AuthenticationPrincipal SchoolUser me, Model model) {
        model.addAttribute("articles", news.listFor(me.id()));
        model.addAttribute("sections", news.sectionsFor(me.id()));
        return "admin/news/list";
    }

    @PostMapping
    String create(@AuthenticationPrincipal SchoolUser me, @RequestParam String title,
                  @RequestParam(required = false) SiteSection section, RedirectAttributes redirect) {
        try {
            NewsArticle article = news.create(title, section, me.id());
            return "redirect:/admin/news/" + article.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/news";
        }
    }

    @GetMapping("/{id}")
    String edit(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, Model model) {
        NewsArticle article = news.getFor(id, me.id());
        return show(me, article, NewsForm.of(article), model);
    }

    @PostMapping("/{id}")
    String save(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @Valid @ModelAttribute("form") NewsForm form,
                BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                news.update(id, form.toDraft(), me.id());
                redirect.addFlashAttribute("notice", "Noticia guardada");
                return "redirect:/admin/news/" + id;
            } catch (RuleViolation e) {
                errors.reject("news", e.getMessage());
            }
        }
        return show(me, news.getFor(id, me.id()), form, model);
    }

    @PostMapping("/{id}/submit")
    String submit(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Enviada a revisión: te avisaremos si la devuelven con comentarios",
                () -> news.submitForReview(id, me.id()));
    }

    /** {@code publishAt} en hora del colegio; vacío = publicar ahora. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('NEWS_PUBLISH')")
    String approve(@AuthenticationPrincipal SchoolUser me, @PathVariable long id,
                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishAt,
                   RedirectAttributes redirect) {
        return run(redirect, id, publishAt == null ? "Noticia publicada" : "Noticia programada",
                () -> news.approve(id, time.toInstant(publishAt), me.id()));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('NEWS_PUBLISH')")
    String returnToDraft(@AuthenticationPrincipal SchoolUser me, @PathVariable long id,
                         @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return run(redirect, id, "Devuelta a borrador; se avisó por correo a quien la escribió",
                () -> news.returnToDraft(id, note, me.id()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('NEWS_PUBLISH')")
    String archive(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Noticia archivada: ya no aparece en el sitio", () -> news.archive(id, me.id()));
    }

    @PostMapping("/{id}/delete")
    String delete(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        try {
            news.delete(id, me.id());
            redirect.addFlashAttribute("notice", "Borrador eliminado");
            return "redirect:/admin/news";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/news/" + id;
        }
    }

    // --- Categorías ---

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('NEWS_PUBLISH')")
    String categories(Model model) {
        model.addAttribute("categories", categories.list());
        return "admin/news/categories";
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('NEWS_PUBLISH')")
    String addCategory(@RequestParam String name, RedirectAttributes redirect) {
        try {
            categories.add(name);
            redirect.addFlashAttribute("notice", "Categoría agregada");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/news/categories";
    }

    @PostMapping("/categories/{id}/delete")
    @PreAuthorize("hasAuthority('NEWS_PUBLISH')")
    String deleteCategory(@PathVariable long id, RedirectAttributes redirect) {
        try {
            categories.delete(id);
            redirect.addFlashAttribute("notice", "Categoría eliminada");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/news/categories";
    }

    private String show(SchoolUser me, NewsArticle article, NewsForm form, Model model) {
        model.addAttribute("article", article);
        model.addAttribute("form", form);
        model.addAttribute("categories", categories.list());
        model.addAttribute("gradeLevels", gradeLevels.findAllByOrderBySortOrderAsc());
        model.addAttribute("sections", news.sectionsFor(me.id()));
        model.addAttribute("canPublish", me.can(Permission.NEWS_PUBLISH));
        model.addAttribute("publishAtLocal", time.toLocal(article.getPublishAt()));
        model.addAttribute("images", media.displayableImages());
        return "admin/news/edit";
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/news/" + id;
    }
}
