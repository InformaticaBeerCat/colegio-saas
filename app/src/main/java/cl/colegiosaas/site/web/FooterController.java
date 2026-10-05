package cl.colegiosaas.site.web;

import cl.colegiosaas.site.FooterService;
import cl.colegiosaas.site.SocialLink;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pie de página y datos de contacto del colegio (CFG-05, PUB-04). Toca el perfil del colegio, por eso
 * pide también SCHOOL_SETTINGS.
 */
@Controller
@RequestMapping("/admin/footer")
@PreAuthorize("hasAuthority('SITE_DESIGN') and hasAuthority('SCHOOL_SETTINGS')")
class FooterController {

    private final FooterService footer;

    FooterController(FooterService footer) {
        this.footer = footer;
    }

    @ModelAttribute("networks")
    SocialLink.Network[] networks() {
        return SocialLink.Network.values();
    }

    @GetMapping
    String edit(Model model) {
        model.addAttribute("form", FooterForm.of(footer.current()));
        return "admin/footer";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("form") FooterForm form, BindingResult errors, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                footer.update(form.toSettings(footer.current().address()));
                redirect.addFlashAttribute("notice", "Pie de página y datos de contacto guardados");
                return "redirect:/admin/footer";
            } catch (IllegalArgumentException e) {
                errors.reject("footer", e.getMessage());
            }
        }
        return "admin/footer";
    }
}
