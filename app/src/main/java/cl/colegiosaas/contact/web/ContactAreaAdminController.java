package cl.colegiosaas.contact.web;

import cl.colegiosaas.contact.ContactService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Áreas que reciben consultas y su correo de aviso (COM-01). */
@Controller
@RequestMapping("/admin/contact-areas")
@RequiresFeature(Feature.CONTACT)
@PreAuthorize("hasAuthority('SCHOOL_SETTINGS')")
class ContactAreaAdminController {

    private final ContactService contact;

    ContactAreaAdminController(ContactService contact) {
        this.contact = contact;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("areas", contact.allAreas());
        return "admin/inquiries/areas";
    }

    @PostMapping
    String save(@RequestParam(required = false) Long id, @RequestParam String name, @RequestParam String notifyEmail,
                @RequestParam(required = false) String description, @RequestParam(defaultValue = "0") int sortOrder,
                @RequestParam(defaultValue = "false") boolean active, RedirectAttributes redirect) {
        try {
            contact.saveArea(id, name, notifyEmail, description, sortOrder, active);
            redirect.addFlashAttribute("notice", id == null ? "Área creada" : "Área guardada");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/contact-areas";
    }
}
