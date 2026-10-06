package cl.colegiosaas.privacy.web;

import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
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

/** Textos legales y avisos de privacidad en el panel (DOC-06, PRV-01). */
@Controller
@RequestMapping("/admin/legal")
@PreAuthorize("hasAuthority('PRIVACY')")
class LegalTextAdminController {

    private final LegalTextService texts;

    LegalTextAdminController(LegalTextService texts) {
        this.texts = texts;
    }

    @GetMapping
    String overview(Model model) {
        model.addAttribute("kinds", texts.overview());
        return "admin/privacy/legal-list";
    }

    /** Abre (o retoma) el borrador de la versión siguiente. */
    @PostMapping("/{kind}/draft")
    String openDraft(@PathVariable LegalTextKind kind, @RequestParam(defaultValue = "false") boolean fromTemplate) {
        LegalText draft = texts.openDraft(kind, fromTemplate);
        return "redirect:/admin/legal/texts/" + draft.getId();
    }

    @GetMapping("/{kind}/history")
    String history(@PathVariable LegalTextKind kind, Model model) {
        model.addAttribute("kind", kind);
        model.addAttribute("versions", texts.history(kind));
        return "admin/privacy/legal-history";
    }

    @GetMapping("/texts/{id}")
    String edit(@PathVariable long id, Model model) {
        LegalText text = texts.get(id);
        model.addAttribute("text", text);
        model.addAttribute("current", texts.current(text.getKind()).orElse(null));
        return "admin/privacy/legal-edit";
    }

    @PostMapping("/texts/{id}")
    String save(@PathVariable long id, @RequestParam String title, @RequestParam String content, RedirectAttributes redirect) {
        try {
            texts.edit(id, title, content);
            redirect.addFlashAttribute("notice", "Borrador guardado");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/legal/texts/" + id;
    }

    @PostMapping("/texts/{id}/publish")
    String publish(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        try {
            texts.publish(id, me.id());
            redirect.addFlashAttribute("notice", "Versión publicada: desde ahora los formularios piden aceptar esta versión");
            return "redirect:/admin/legal";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/legal/texts/" + id;
        }
    }

    @PostMapping("/texts/{id}/discard")
    String discard(@PathVariable long id, RedirectAttributes redirect) {
        try {
            texts.discard(id);
            redirect.addFlashAttribute("notice", "Borrador descartado");
            return "redirect:/admin/legal";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/legal/texts/" + id;
        }
    }
}
