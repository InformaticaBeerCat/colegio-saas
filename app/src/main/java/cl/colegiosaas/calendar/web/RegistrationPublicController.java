package cl.colegiosaas.calendar.web;

import cl.colegiosaas.calendar.CalendarService;
import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventRegistration;
import cl.colegiosaas.calendar.RegistrationService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.publicsite.EventPage;
import cl.colegiosaas.publicsite.PublicPages;
import cl.colegiosaas.shared.forms.FormGuard;
import cl.colegiosaas.shared.forms.FormGuards;
import cl.colegiosaas.shared.web.RuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Inscripción pública a eventos y el enlace secreto para cancelarla (EVE-02). */
@Controller
@RequiresFeature(Feature.EVENTS)
class RegistrationPublicController {

    private final CalendarService calendar;
    private final RegistrationService registrations;
    private final EventPage eventPage;
    private final PublicPages pages;
    private final FormGuard guard;

    RegistrationPublicController(CalendarService calendar, RegistrationService registrations, EventPage eventPage,
                                 PublicPages pages, FormGuard guard) {
        this.calendar = calendar;
        this.registrations = registrations;
        this.eventPage = eventPage;
        this.pages = pages;
        this.guard = guard;
    }

    @PostMapping("/calendario/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}/inscripcion")
    String register(@PathVariable String slug, @RequestParam(required = false) String name,
                    @RequestParam(required = false) String email, @RequestParam(required = false) String phone,
                    @RequestParam(defaultValue = "1") int attendees, @RequestParam(required = false) Long courseId,
                    @RequestParam(required = false) String studentName, @RequestParam(defaultValue = "false") boolean consent,
                    @RequestParam(name = FormGuard.HONEYPOT, required = false) String honeypot,
                    @RequestParam(name = FormGuard.STAMP, required = false) String stamp,
                    HttpServletRequest request, Model model, RedirectAttributes redirect) {
        Event event = calendar.publishedBySlug(slug);
        RegistrationService.Submission form = new RegistrationService.Submission(name, email, phone, attendees, courseId,
                studentName, consent);
        FormGuard.Verdict verdict = guard.check("inscripcion", request.getRemoteAddr(), honeypot, stamp);
        if (verdict == FormGuard.Verdict.BOT) {
            redirect.addFlashAttribute("notice", "Recibimos tu inscripción. Revisa tu correo.");
            return "redirect:/calendario/" + slug;
        }
        try {
            if (verdict != FormGuard.Verdict.OK) {
                throw new RuleViolation(FormGuards.message(verdict));
            }
            RegistrationService.Result result = registrations.register(event.getId(), form, RequestOrigin.of(request));
            redirect.addFlashAttribute("notice", result.registration().getWaitlistPosition() == null
                    ? "¡Listo! Tu inscripción quedó confirmada. Te enviamos los detalles por correo."
                    : "El evento está lleno: quedaste en el lugar " + result.registration().getWaitlistPosition()
                    + " de la lista de espera. Te avisaremos por correo si se libera un cupo.");
            return "redirect:/calendario/" + slug;
        } catch (RuleViolation e) {
            model.addAttribute("problem", e.getMessage());
            model.addAttribute("form", form);
            return eventPage.render(model, event);
        }
    }

    @GetMapping("/inscripciones/{token}")
    String manage(@PathVariable String token, Model model) {
        EventRegistration registration = registrations.byToken(token);
        pages.prepare(model, "Mi inscripción", null);
        model.addAttribute("noindex", true);
        model.addAttribute("registration", registration);
        model.addAttribute("token", token);
        return "public/calendar/registration";
    }

    @PostMapping("/inscripciones/{token}/cancelar")
    String cancel(@PathVariable String token, RedirectAttributes redirect) {
        try {
            registrations.cancelByToken(token);
            redirect.addFlashAttribute("notice", "Cancelaste tu inscripción. Gracias por avisar: el lugar pasa a otra familia.");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/inscripciones/" + token;
    }
}
