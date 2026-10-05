package cl.colegiosaas.setup;

import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.site.SiteDesignService;
import cl.colegiosaas.site.Theme;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Asistente de marca (CFG-01), segunda parte del primer arranque: con la instalación hecha, el colegio
 * elige su tema y obtiene un sitio navegable en un paso. Todo se puede cambiar después.
 */
@Controller
@RequestMapping("/admin/welcome")
@PreAuthorize("hasAuthority('SITE_DESIGN') and hasAuthority('PAGES')")
class WelcomeController {

    private final SiteDesignService designs;
    private final StarterContent starter;
    private final SchoolRepository schools;

    WelcomeController(SiteDesignService designs, StarterContent starter, SchoolRepository schools) {
        this.designs = designs;
        this.starter = starter;
        this.schools = schools;
    }

    @GetMapping
    String wizard(Model model) {
        model.addAttribute("themes", Theme.values());
        model.addAttribute("current", designs.published().theme() + ":" + designs.published().variant());
        model.addAttribute("starterExists", starter.exists());
        return "admin/welcome";
    }

    /**
     * @param choice tema y variante en un solo valor ("classic:navy"), porque se eligen con un solo grupo de radios
     */
    @PostMapping
    String finish(@RequestParam String choice, @RequestParam(defaultValue = "false") boolean createPages,
                  RedirectAttributes redirect) {
        String[] parts = choice.split(":", 2);
        Theme theme = Theme.byId(parts[0]).orElse(null);
        if (theme == null || parts.length < 2 || theme.variant(parts[1]).isEmpty()) {
            redirect.addFlashAttribute("problem", "Elige uno de los temas");
            return "redirect:/admin/welcome";
        }
        designs.applyTheme(theme, parts[1]);
        designs.publish();
        if (createPages) {
            starter.create(schools.findSingleton().map(School::getName).orElse("Nuestro colegio"));
        }
        redirect.addFlashAttribute("notice", createPages
                ? "Listo: el sitio ya tiene portada y menú. Completa las páginas en borrador y publícalas."
                : "Tema aplicado y publicado.");
        return "redirect:/admin";
    }
}
