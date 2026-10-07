package cl.colegiosaas.contact.web;

import cl.colegiosaas.contact.ContactService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Bandeja de consultas del formulario de contacto (COM-02). */
@Controller
@RequestMapping("/admin/inquiries")
@RequiresFeature(Feature.CONTACT)
@PreAuthorize("hasAuthority('INQUIRIES')")
class InquiryAdminController {

    private final ContactService contact;

    InquiryAdminController(ContactService contact) {
        this.contact = contact;
    }

    @GetMapping
    String inbox(@RequestParam(required = false) Long area, @RequestParam(defaultValue = "OPEN") ContactService.Folder folder,
                 Model model) {
        model.addAttribute("inquiries", contact.inbox(area, folder));
        model.addAttribute("areas", contact.allAreas());
        model.addAttribute("area", area);
        model.addAttribute("folder", folder);
        model.addAttribute("folders", ContactService.Folder.values());
        model.addAttribute("averageHours", contact.averageFirstResponseHours());
        return "admin/inquiries/inbox";
    }

    @GetMapping("/{id}")
    String show(@PathVariable long id, Model model) {
        model.addAttribute("view", contact.view(id));
        model.addAttribute("areas", contact.allAreas());
        return "admin/inquiries/show";
    }

    @PostMapping("/{id}/reply")
    String reply(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam String text, RedirectAttributes redirect) {
        return run(redirect, id, "Respuesta enviada por correo", () -> contact.reply(id, text, me.id()));
    }

    @PostMapping("/{id}/note")
    String note(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam String text, RedirectAttributes redirect) {
        return run(redirect, id, "Nota agregada", () -> contact.addNote(id, text, me.id()));
    }

    @PostMapping("/{id}/assign")
    String assign(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Consulta asignada a ti", () -> contact.assignToMe(id, me.id()));
    }

    @PostMapping("/{id}/route")
    String route(@PathVariable long id, @RequestParam long areaId, RedirectAttributes redirect) {
        return run(redirect, id, "Consulta derivada; el área recibió el aviso", () -> contact.route(id, areaId));
    }

    @PostMapping("/{id}/resolve")
    String resolve(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Consulta resuelta", () -> contact.resolve(id));
    }

    @PostMapping("/{id}/spam")
    String spam(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Marcada como spam", () -> contact.markAsSpam(id));
    }

    @PostMapping("/{id}/reopen")
    String reopen(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Consulta reabierta", () -> contact.reopen(id));
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/inquiries/" + id;
    }
}
