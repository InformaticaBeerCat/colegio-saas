package cl.colegiosaas.calendar.web;

import cl.colegiosaas.calendar.CalendarService;
import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventRegistration;
import cl.colegiosaas.calendar.RegistrationService;
import cl.colegiosaas.calendar.RegistrationStatus;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/** Inscripción de un evento en el panel: aforo, lista de espera, inscritos y asistencia (EVE-01, EVE-02). */
@Controller
@RequestMapping("/admin/events/{eventId}/registrations")
@RequiresFeature(Feature.EVENTS)
@PreAuthorize("hasAuthority('EVENTS')")
class RegistrationAdminController {

    private final CalendarService calendar;
    private final RegistrationService registrations;

    RegistrationAdminController(CalendarService calendar, RegistrationService registrations) {
        this.calendar = calendar;
        this.registrations = registrations;
    }

    @GetMapping
    String roster(@PathVariable long eventId, Model model) {
        Event event = calendar.get(eventId);
        List<EventRegistration> roster = registrations.roster(eventId);
        model.addAttribute("event", event);
        model.addAttribute("roster", roster);
        model.addAttribute("offer", registrations.offer(event).orElse(null));
        model.addAttribute("confirmedPeople", roster.stream().filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED
                || r.getStatus() == RegistrationStatus.ATTENDED).mapToInt(EventRegistration::getAttendees).sum());
        model.addAttribute("waitlisted", roster.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).count());
        return "admin/calendar/registrations";
    }

    @PostMapping("/settings")
    String configure(@PathVariable long eventId, @RequestParam(defaultValue = "false") boolean enabled,
                     @RequestParam(required = false) Integer capacity, @RequestParam(defaultValue = "false") boolean waitlist,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime closesAt,
                     RedirectAttributes redirect) {
        return run(redirect, eventId, enabled ? "Inscripción abierta" : "Inscripción cerrada",
                () -> registrations.configure(eventId, enabled, capacity, waitlist, closesAt));
    }

    @PostMapping("/{id}/cancel")
    String cancel(@PathVariable long eventId, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, eventId, "Inscripción cancelada", () -> registrations.cancelFromPanel(id));
    }

    @PostMapping("/{id}/attended")
    String attended(@PathVariable long eventId, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, eventId, "Asistencia registrada", () -> registrations.markAttended(id));
    }

    @GetMapping("/export")
    ResponseEntity<byte[]> export(@PathVariable long eventId) {
        Event event = calendar.get(eventId);
        // BOM para que Excel abra el archivo en UTF-8.
        byte[] csv = ("﻿" + registrations.rosterCsv(eventId)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("inscritos-" + event.getSlug() + ".csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }

    private static String run(RedirectAttributes redirect, long eventId, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/events/" + eventId + "/registrations";
    }
}
