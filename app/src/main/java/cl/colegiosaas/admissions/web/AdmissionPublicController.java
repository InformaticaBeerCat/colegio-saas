package cl.colegiosaas.admissions.web;

import cl.colegiosaas.admissions.AdmissionService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.platform.SchoolTime;
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

/**
 * Página de admisión (ADM-01..03, ADM-07, ADM-08) con el registro de interés. Las campañas llegan con
 * {@code utm_source}/{@code utm_campaign} en la URL y se guardan con el registro.
 */
@Controller
@RequiresFeature(Feature.SAE_ADMISSIONS)
class AdmissionPublicController {

    private static final String THANKS = "¡Gracias! Registramos tu interés y te enviamos la información del proceso por correo.";

    private final AdmissionService admissions;
    private final LegalTextService legalTexts;
    private final PublicPages pages;
    private final FormGuard guard;
    private final SchoolTime time;

    AdmissionPublicController(AdmissionService admissions, LegalTextService legalTexts, PublicPages pages, FormGuard guard,
                              SchoolTime time) {
        this.admissions = admissions;
        this.legalTexts = legalTexts;
        this.pages = pages;
        this.guard = guard;
        this.time = time;
    }

    @GetMapping("/admision")
    String page(@RequestParam(name = "utm_source", required = false) String utmSource,
                @RequestParam(name = "utm_campaign", required = false) String utmCampaign, Model model) {
        prepare(model);
        model.addAttribute("form", new AdmissionService.Interest(null, null, null, null, false, false, utmSource, utmCampaign));
        return "public/admissions/page";
    }

    @PostMapping("/admision/interes")
    String register(@RequestParam(required = false) String name, @RequestParam(required = false) String email,
                    @RequestParam(required = false) String phone, @RequestParam(required = false) Long gradeLevelId,
                    @RequestParam(defaultValue = "false") boolean consent, @RequestParam(defaultValue = "false") boolean followUp,
                    @RequestParam(required = false) String utmSource, @RequestParam(required = false) String utmCampaign,
                    @RequestParam(name = FormGuard.HONEYPOT, required = false) String honeypot,
                    @RequestParam(name = FormGuard.STAMP, required = false) String stamp,
                    HttpServletRequest request, Model model, RedirectAttributes redirect) {
        AdmissionService.Interest form = new AdmissionService.Interest(name, email, phone, gradeLevelId, consent, followUp,
                utmSource, utmCampaign);
        FormGuard.Verdict verdict = guard.check("admision", request.getRemoteAddr(), honeypot, stamp);
        if (verdict == FormGuard.Verdict.BOT) {
            redirect.addFlashAttribute("notice", THANKS);
            return "redirect:/admision#interes";
        }
        try {
            if (verdict != FormGuard.Verdict.OK) {
                throw new RuleViolation(FormGuards.message(verdict));
            }
            admissions.registerInterest(form, RequestOrigin.of(request));
            redirect.addFlashAttribute("notice", THANKS);
            return "redirect:/admision#interes";
        } catch (RuleViolation e) {
            prepare(model);
            model.addAttribute("problem", e.getMessage());
            model.addAttribute("form", form);
            return "public/admissions/page";
        }
    }

    private void prepare(Model model) {
        AdmissionService.Page page = admissions.page();
        pages.prepare(model, "Admisión " + page.settings().getProcessYear(),
                "Proceso de admisión " + page.settings().getProcessYear() + ": fechas, vacantes, visitas y cómo postular");
        model.addAttribute("admission", page);
        model.addAttribute("today", time.today());
        model.addAttribute("privacyNotice", legalTexts.current(LegalTextKind.NOTICE_ADMISSIONS).orElse(null));
    }
}
