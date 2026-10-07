package cl.colegiosaas.privacy.web;

import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.privacy.DataSubjectRequest;
import cl.colegiosaas.privacy.DataSubjectRequestService;
import cl.colegiosaas.privacy.DataSubjectRight;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.publicsite.PublicPages;
import cl.colegiosaas.shared.forms.FormGuard;
import cl.colegiosaas.shared.forms.FormGuards;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Privacidad en el sitio público: textos legales vigentes y sus versiones anteriores (DOC-06), preferencias
 * de cookies (PRV-03) y el formulario para ejercer derechos con su consulta de estado (PRV-05).
 */
@Controller
class PrivacyPublicController {

    /** Solo rutas del propio sitio: el parámetro "volver" no puede llevar a otro dominio. */
    private static final Pattern LOCAL_PATH = Pattern.compile("^/(?![/\\\\])[^\\s]*$");

    private final LegalTextService texts;
    private final DataSubjectRequestService requests;
    private final CookiePreferences cookies;
    private final SchoolRepository schools;
    private final PublicPages pages;
    private final FormGuard guard;

    PrivacyPublicController(LegalTextService texts, DataSubjectRequestService requests, CookiePreferences cookies,
                            SchoolRepository schools, PublicPages pages, FormGuard guard) {
        this.texts = texts;
        this.requests = requests;
        this.cookies = cookies;
        this.schools = schools;
        this.pages = pages;
        this.guard = guard;
    }

    @GetMapping("/privacidad")
    String hub(Model model) {
        pages.prepare(model, "Privacidad", "Cómo el colegio trata los datos personales y cómo ejercer tus derechos");
        model.addAttribute("policy", texts.current(LegalTextKind.PRIVACY_POLICY).orElse(null));
        model.addAttribute("others", Arrays.stream(LegalTextKind.values())
                .filter(k -> k != LegalTextKind.PRIVACY_POLICY)
                .map(texts::current).flatMap(Optional::stream).toList());
        return "public/privacy/hub";
    }

    @GetMapping("/privacidad/{slug:[a-z-]+}")
    String text(@PathVariable String slug, Model model) {
        LegalTextKind kind = kind(slug);
        LegalText text = texts.current(kind).orElseThrow(() -> new NotFound("Este texto todavía no está publicado"));
        return render(model, text, true);
    }

    @GetMapping("/privacidad/{slug:[a-z-]+}/v{version:\\d+}")
    String version(@PathVariable String slug, @PathVariable int version, Model model) {
        LegalTextKind kind = kind(slug);
        LegalText text = texts.published(kind, version).orElseThrow(() -> new NotFound("Esa versión no existe"));
        LegalText current = texts.current(kind).orElseThrow();
        return render(model, text, current.getId().equals(text.getId()));
    }

    @PostMapping("/privacidad/cookies/preferencias")
    String saveCookies(@RequestParam(defaultValue = "false") boolean analitica, @RequestParam(required = false) String volver,
                       HttpServletRequest request, HttpServletResponse response) {
        cookies.write(request, response, analitica);
        return "redirect:" + (volver != null && LOCAL_PATH.matcher(volver).matches() ? volver : "/privacidad/cookies");
    }

    @GetMapping("/privacidad/derechos")
    String rightsForm(Model model) {
        prepareForm(model);
        return "public/privacy/rights";
    }

    @PostMapping("/privacidad/derechos")
    String submit(@RequestParam(required = false) DataSubjectRight right, @RequestParam(required = false) String name,
                  @RequestParam(required = false) String email, @RequestParam(defaultValue = "false") boolean onBehalfOfMinor,
                  @RequestParam(required = false) String details, @RequestParam(defaultValue = "false") boolean noticeRead,
                  @RequestParam(name = FormGuard.HONEYPOT, required = false) String honeypot,
                  @RequestParam(name = FormGuard.STAMP, required = false) String stamp,
                  HttpServletRequest request, Model model, RedirectAttributes redirect) {
        DataSubjectRequestService.Submission form = new DataSubjectRequestService.Submission(right, name, email,
                onBehalfOfMinor, details, noticeRead);
        FormGuard.Verdict verdict = guard.check("derechos", request.getRemoteAddr(), honeypot, stamp);
        if (verdict == FormGuard.Verdict.BOT) {
            // Se responde igual que siempre, sin guardar nada.
            redirect.addFlashAttribute("notice", "Recibimos tu solicitud. Te enviamos el código de seguimiento por correo.");
            return "redirect:/privacidad/derechos/estado";
        }
        if (verdict != FormGuard.Verdict.OK) {
            prepareForm(model);
            model.addAttribute("problem", FormGuards.message(verdict));
            model.addAttribute("form", form);
            return "public/privacy/rights";
        }
        try {
            DataSubjectRequest saved = requests.submit(form, RequestOrigin.of(request));
            redirect.addFlashAttribute("notice", "Recibimos tu solicitud. Te enviamos el código de seguimiento por correo.");
            redirect.addFlashAttribute("submitted", saved.getTrackingCode());
            return "redirect:/privacidad/derechos/estado";
        } catch (RuleViolation e) {
            prepareForm(model);
            model.addAttribute("problem", e.getMessage());
            model.addAttribute("form", form);
            return "public/privacy/rights";
        }
    }

    @GetMapping("/privacidad/derechos/estado")
    String status(@RequestParam(required = false) String codigo, Model model) {
        pages.prepare(model, "Estado de mi solicitud", null);
        model.addAttribute("noindex", true);
        model.addAttribute("code", codigo);
        if (codigo != null && !codigo.isBlank()) {
            model.addAttribute("found", requests.byTrackingCode(codigo).orElse(null));
            model.addAttribute("searched", true);
        }
        return "public/privacy/status";
    }

    private String render(Model model, LegalText text, boolean isCurrent) {
        pages.prepare(model, text.getTitle(), null);
        model.addAttribute("text", text);
        model.addAttribute("isCurrent", isCurrent);
        List<LegalText> history = texts.history(text.getKind());
        model.addAttribute("history", history.size() > 1 ? history : List.of());
        return "public/privacy/text";
    }

    private void prepareForm(Model model) {
        pages.prepare(model, "Ejercer mis derechos", "Solicita acceso, rectificación, supresión u otros derechos sobre tus datos personales");
        model.addAttribute("rights", DataSubjectRight.values());
        model.addAttribute("privacyNotice", texts.current(LegalTextKind.NOTICE_DATA_REQUESTS).orElse(null));
        model.addAttribute("contactEmail", schools.findSingleton().map(School::getContactEmail).orElse(null));
    }

    private static LegalTextKind kind(String slug) {
        return LegalTextKind.bySlug(slug).orElseThrow(() -> new NotFound("La página no existe"));
    }
}
