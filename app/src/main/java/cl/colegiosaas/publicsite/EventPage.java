package cl.colegiosaas.publicsite;

import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.RegistrationService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Features;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** Modelo de la página pública de un evento: datos, y la inscripción si el módulo de eventos está activo (EVE-02). */
@Component
public class EventPage {

    private final PublicPages pages;
    private final RegistrationService registrations;
    private final LegalTextService legalTexts;
    private final Features features;

    EventPage(PublicPages pages, RegistrationService registrations, LegalTextService legalTexts, Features features) {
        this.pages = pages;
        this.registrations = registrations;
        this.legalTexts = legalTexts;
        this.features = features;
    }

    public String render(Model model, Event event) {
        pages.prepare(model, event.getTitle(), event.getDescription());
        model.addAttribute("event", event);
        if (features.on(Feature.EVENTS)) {
            registrations.offer(event).ifPresent(offer -> model.addAttribute("offer", offer));
            model.addAttribute("privacyNotice", legalTexts.current(LegalTextKind.NOTICE_EVENTS).orElse(null));
            model.addAttribute("maxAttendees", RegistrationService.MAX_ATTENDEES);
        }
        return "public/calendar/event";
    }
}
