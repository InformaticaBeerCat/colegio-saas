package cl.colegiosaas.site.web;

import cl.colegiosaas.site.DesignRejectedException;
import cl.colegiosaas.site.DesignReview;
import cl.colegiosaas.site.FontCatalog;
import cl.colegiosaas.site.SiteDesign;
import cl.colegiosaas.site.SiteDesignService;
import cl.colegiosaas.site.Theme;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Editor de marca (CFG-02, CFG-03, CFG-08): elegir tema, ajustar tokens con verificación de contraste,
 * previsualizar el borrador y publicarlo.
 */
@Controller
@RequestMapping("/admin/design")
@PreAuthorize("hasAuthority('SITE_DESIGN')")
class DesignController {

    private final SiteDesignService designs;

    DesignController(SiteDesignService designs) {
        this.designs = designs;
    }

    @ModelAttribute
    void options(Model model) {
        model.addAttribute("themes", Theme.values());
        model.addAttribute("fonts", FontCatalog.values());
        model.addAttribute("radii", SiteDesign.CornerRadius.values());
        model.addAttribute("shadows", SiteDesign.Shadow.values());
        model.addAttribute("schemes", SiteDesign.ColorScheme.values());
    }

    @GetMapping
    String edit(Model model) {
        SiteDesign draft = designs.draft();
        return show(model, DesignForm.of(draft), DesignReview.of(draft));
    }

    @PostMapping
    String save(@Valid @ModelAttribute("form") DesignForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return show(model, form, null);
        }
        SiteDesign design = form.toDesign();
        try {
            designs.saveDraft(design);
        } catch (DesignRejectedException e) {
            errors.reject("design", "El diseño no cumple el contraste mínimo o usa una fuente no disponible. Revisa los puntos marcados.");
            return show(model, form, e.review());
        }
        redirect.addFlashAttribute("notice", "Borrador guardado. Revísalo en la vista previa y publícalo cuando esté listo.");
        return "redirect:/admin/design";
    }

    @PostMapping("/theme")
    String applyTheme(@RequestParam String theme, @RequestParam String variant, RedirectAttributes redirect) {
        Theme chosen = Theme.byId(theme).orElse(null);
        if (chosen == null || chosen.variant(variant).isEmpty()) {
            redirect.addFlashAttribute("problem", "Elige un tema y una variante");
        } else {
            designs.applyTheme(chosen, variant);
            redirect.addFlashAttribute("notice", "Tema aplicado al borrador. Ajusta los colores si quieres y publícalo.");
        }
        return "redirect:/admin/design";
    }

    @PostMapping("/publish")
    String publish(RedirectAttributes redirect) {
        try {
            designs.publish();
            redirect.addFlashAttribute("notice", "Diseño publicado: ya lo ven los visitantes.");
        } catch (DesignRejectedException e) {
            redirect.addFlashAttribute("problem", "No se puede publicar: " + e.getMessage());
        }
        return "redirect:/admin/design";
    }

    @PostMapping("/discard")
    String discard(RedirectAttributes redirect) {
        designs.discardDraft();
        redirect.addFlashAttribute("notice", "Borrador descartado: vuelves al diseño publicado.");
        return "redirect:/admin/design";
    }

    private String show(Model model, DesignForm form, DesignReview review) {
        model.addAttribute("form", form);
        model.addAttribute("review", review);
        model.addAttribute("currentTheme", Theme.byId(form.getTheme()).orElse(Theme.CLASSIC));
        model.addAttribute("unpublished", designs.hasUnpublishedChanges());
        return "admin/design";
    }
}
