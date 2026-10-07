package cl.colegiosaas.contact.web;

import cl.colegiosaas.contact.ContactService;
import cl.colegiosaas.contact.Inquiry;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.publicsite.PublicPages;
import cl.colegiosaas.shared.forms.FormGuard;
import cl.colegiosaas.shared.forms.FormGuards;
import cl.colegiosaas.shared.web.RuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Formulario de contacto del sitio (COM-01) con antispam (COM-08) y acceso a WhatsApp (COM-03). */
@Controller
@RequiresFeature(Feature.CONTACT)
class ContactPublicController {

    private static final String SENT = "Recibimos tu mensaje. Te enviamos el número de atención por correo.";

    private final ContactService contact;
    private final LegalTextService legalTexts;
    private final PublicPages pages;
    private final FormGuard guard;

    ContactPublicController(ContactService contact, LegalTextService legalTexts, PublicPages pages, FormGuard guard) {
        this.contact = contact;
        this.legalTexts = legalTexts;
        this.pages = pages;
        this.guard = guard;
    }

    @GetMapping("/contacto")
    String form(@RequestParam(required = false) Long area, Model model) {
        prepare(model);
        if (area != null) {
            model.addAttribute("form", new ContactService.Submission(area, null, null, null, null, null, false));
        }
        return "public/contact/form";
    }

    @PostMapping("/contacto")
    String submit(@RequestParam(required = false) Long areaId, @RequestParam(required = false) String name,
                  @RequestParam(required = false) String email, @RequestParam(required = false) String phone,
                  @RequestParam(required = false) String subject, @RequestParam(required = false) String message,
                  @RequestParam(defaultValue = "false") boolean consent,
                  @RequestParam(name = FormGuard.HONEYPOT, required = false) String honeypot,
                  @RequestParam(name = FormGuard.STAMP, required = false) String stamp,
                  HttpServletRequest request, Model model, RedirectAttributes redirect) {
        ContactService.Submission form = new ContactService.Submission(areaId, name, email, phone, subject, message, consent);
        FormGuard.Verdict verdict = guard.check("contacto", request.getRemoteAddr(), honeypot, stamp);
        if (verdict == FormGuard.Verdict.BOT) {
            redirect.addFlashAttribute("notice", SENT);
            return "redirect:/contacto/enviado";
        }
        try {
            if (verdict != FormGuard.Verdict.OK) {
                throw new RuleViolation(FormGuards.message(verdict));
            }
            Inquiry inquiry = contact.submit(form, RequestOrigin.of(request));
            redirect.addFlashAttribute("notice", SENT);
            redirect.addFlashAttribute("ticket", inquiry.getTicketCode());
            return "redirect:/contacto/enviado";
        } catch (RuleViolation e) {
            prepare(model);
            model.addAttribute("problem", e.getMessage());
            model.addAttribute("form", form);
            return "public/contact/form";
        }
    }

    @GetMapping("/contacto/enviado")
    String sent(Model model) {
        pages.prepare(model, "Mensaje enviado", null);
        model.addAttribute("noindex", true);
        return "public/contact/sent";
    }

    private void prepare(Model model) {
        pages.prepare(model, "Contacto", "Escríbenos: tu mensaje llega directo al área que corresponde");
        model.addAttribute("areas", contact.publicAreas());
        model.addAttribute("privacyNotice", legalTexts.current(LegalTextKind.NOTICE_CONTACT).orElse(null));
    }
}
