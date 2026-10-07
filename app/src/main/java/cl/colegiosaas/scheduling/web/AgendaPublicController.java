package cl.colegiosaas.scheduling.web;

import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.publicsite.PublicPages;
import cl.colegiosaas.scheduling.Appointment;
import cl.colegiosaas.scheduling.AppointmentType;
import cl.colegiosaas.scheduling.BookingService;
import cl.colegiosaas.scheduling.MeetingMode;
import cl.colegiosaas.scheduling.SlotFinder;
import cl.colegiosaas.shared.forms.FormGuard;
import cl.colegiosaas.shared.forms.FormGuards;
import cl.colegiosaas.shared.web.RuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agenda pública (AGE-03): tipos de cita, horas libres y reserva; y el enlace secreto de cada cita para
 * descargarla al calendario, reprogramarla o cancelarla (AGE-04, AGE-05).
 */
@Controller
@RequiresFeature(Feature.SCHEDULING)
class AgendaPublicController {

    private final BookingService booking;
    private final LegalTextService legalTexts;
    private final PublicPages pages;
    private final FormGuard guard;

    AgendaPublicController(BookingService booking, LegalTextService legalTexts, PublicPages pages, FormGuard guard) {
        this.booking = booking;
        this.legalTexts = legalTexts;
        this.pages = pages;
        this.guard = guard;
    }

    @GetMapping("/agenda")
    String types(Model model) {
        pages.prepare(model, "Agenda tu cita", "Visitas guiadas y entrevistas: elige el día y la hora que te acomoden");
        model.addAttribute("types", booking.publicTypes());
        return "public/agenda/types";
    }

    @GetMapping("/agenda/{typeId:\\d+}")
    String slots(@PathVariable long typeId, Model model) {
        AppointmentType type = booking.publicType(typeId);
        pages.prepare(model, type.getName(), type.getDescription());
        model.addAttribute("type", type);
        model.addAttribute("days", byDay(booking.slotsFor(typeId)));
        return "public/agenda/slots";
    }

    @GetMapping("/agenda/{typeId:\\d+}/reservar")
    String form(@PathVariable long typeId, @RequestParam("funcionario") long hostId,
                @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start, Model model) {
        prepareForm(model, typeId, hostId, start);
        return "public/agenda/book";
    }

    @PostMapping("/agenda/{typeId:\\d+}/reservar")
    String book(@PathVariable long typeId, @RequestParam("funcionario") long hostId,
                @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                @RequestParam(required = false) MeetingMode mode, @RequestParam(required = false) String name,
                @RequestParam(required = false) String email, @RequestParam(required = false) String phone,
                @RequestParam(required = false) String studentName, @RequestParam(defaultValue = "false") boolean consent,
                @RequestParam(name = FormGuard.HONEYPOT, required = false) String honeypot,
                @RequestParam(name = FormGuard.STAMP, required = false) String stamp,
                HttpServletRequest request, Model model, RedirectAttributes redirect) {
        BookingService.Request form = new BookingService.Request(hostId, start, mode, name, email, phone, studentName, consent);
        FormGuard.Verdict verdict = guard.check("cita", request.getRemoteAddr(), honeypot, stamp);
        if (verdict == FormGuard.Verdict.BOT) {
            redirect.addFlashAttribute("notice", "Recibimos tu reserva. Te enviamos la confirmación por correo.");
            return "redirect:/agenda/confirmada";
        }
        try {
            if (verdict != FormGuard.Verdict.OK) {
                throw new RuleViolation(FormGuards.message(verdict));
            }
            BookingService.Booked booked = booking.book(typeId, form, RequestOrigin.of(request));
            redirect.addFlashAttribute("notice", "Tu cita quedó agendada. Te enviamos la confirmación por correo, con un enlace para cambiarla o cancelarla.");
            redirect.addFlashAttribute("when", BookingService.when(booked.appointment().getStartsAt()));
            redirect.addFlashAttribute("typeName", booked.appointment().getAppointmentType().getName());
            return "redirect:/agenda/confirmada";
        } catch (RuleViolation e) {
            prepareForm(model, typeId, hostId, start);
            model.addAttribute("problem", e.getMessage());
            model.addAttribute("form", form);
            return "public/agenda/book";
        }
    }

    @GetMapping("/agenda/confirmada")
    String booked(Model model) {
        pages.prepare(model, "Cita agendada", null);
        model.addAttribute("noindex", true);
        return "public/agenda/booked";
    }

    // --- Enlace secreto ---

    @GetMapping("/citas/{token}")
    String manage(@PathVariable String token, Model model) {
        Appointment appointment = booking.byToken(token);
        pages.prepare(model, "Mi cita", null);
        model.addAttribute("noindex", true);
        model.addAttribute("appointment", appointment);
        model.addAttribute("when", BookingService.when(appointment.getStartsAt()));
        model.addAttribute("token", token);
        return "public/agenda/manage";
    }

    @GetMapping("/citas/{token}/cita.ics")
    ResponseEntity<String> ics(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("cita.ics").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(booking.ics(token));
    }

    @PostMapping("/citas/{token}/cancelar")
    String cancel(@PathVariable String token, @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        try {
            booking.cancelByToken(token, reason);
            redirect.addFlashAttribute("notice", "Cancelaste tu cita. Gracias por avisar: la hora queda libre para otra familia.");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/citas/" + token;
    }

    @GetMapping("/citas/{token}/reprogramar")
    String rescheduleOptions(@PathVariable String token, Model model) {
        Appointment appointment = booking.byToken(token);
        pages.prepare(model, "Cambiar la hora", null);
        model.addAttribute("noindex", true);
        model.addAttribute("appointment", appointment);
        model.addAttribute("when", BookingService.when(appointment.getStartsAt()));
        model.addAttribute("token", token);
        model.addAttribute("days", byDay(booking.rescheduleOptions(token)));
        return "public/agenda/reschedule";
    }

    @PostMapping("/citas/{token}/reprogramar")
    String reschedule(@PathVariable String token,
                      @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                      RedirectAttributes redirect) {
        try {
            booking.rescheduleByToken(token, start);
            redirect.addFlashAttribute("notice", "Listo: tu cita cambió de hora. Te enviamos la confirmación por correo.");
            return "redirect:/citas/" + token;
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/citas/" + token + "/reprogramar";
        }
    }

    private void prepareForm(Model model, long typeId, long hostId, LocalDateTime start) {
        AppointmentType type = booking.publicType(typeId);
        pages.prepare(model, "Reservar: " + type.getName(), null);
        model.addAttribute("noindex", true);
        model.addAttribute("type", type);
        model.addAttribute("hostId", hostId);
        model.addAttribute("hostName", type.getHosts().stream().filter(h -> h.getId() == hostId).findFirst()
                .map(h -> h.getName()).orElse(null));
        model.addAttribute("start", start);
        model.addAttribute("when", BookingService.when(start));
        model.addAttribute("privacyNotice", legalTexts.current(LegalTextKind.NOTICE_SCHEDULING).orElse(null));
    }

    /** Horas agrupadas por día, en orden. */
    private static Map<LocalDate, List<SlotFinder.Slot>> byDay(List<SlotFinder.Slot> slots) {
        return slots.stream().collect(Collectors.groupingBy(s -> s.start().toLocalDate(), LinkedHashMap::new, Collectors.toList()));
    }
}
