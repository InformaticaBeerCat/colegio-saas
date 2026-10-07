package cl.colegiosaas.scheduling.web;

import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.scheduling.AgendaConfigService;
import cl.colegiosaas.scheduling.Appointment;
import cl.colegiosaas.scheduling.BookingService;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Panel del gestor de agenda (AGE-10): sus citas y su disponibilidad. Quien tiene la agenda de todo el colegio
 * ve y administra la de cualquier funcionario; los demás, solo la propia.
 */
@Controller
@RequestMapping("/admin/scheduling")
@RequiresFeature(Feature.SCHEDULING)
@PreAuthorize("hasAnyAuthority('SCHEDULING_OWN', 'SCHEDULING_ALL')")
class AgendaAdminController {

    private static final int DAYS_SHOWN = 14;

    private final BookingService booking;
    private final AgendaConfigService config;
    private final SchoolTime time;

    AgendaAdminController(BookingService booking, AgendaConfigService config, SchoolTime time) {
        this.booking = booking;
        this.config = config;
        this.time = time;
    }

    @GetMapping
    String agenda(@AuthenticationPrincipal SchoolUser me, @RequestParam(required = false) Long host,
                  @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                  Model model) {
        LocalDate start = from != null ? from : time.today();
        Long hostId = hostFilter(me, host);
        Map<LocalDate, List<Appointment>> days = booking.agenda(hostId, start, start.plusDays(DAYS_SHOWN - 1)).stream()
                .collect(Collectors.groupingBy(a -> a.getStartsAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()));
        model.addAttribute("days", days);
        model.addAttribute("from", start);
        model.addAttribute("previous", start.minusDays(DAYS_SHOWN));
        model.addAttribute("next", start.plusDays(DAYS_SHOWN));
        model.addAttribute("host", hostId);
        model.addAttribute("manageAll", me.can(Permission.SCHEDULING_ALL));
        model.addAttribute("hosts", me.can(Permission.SCHEDULING_ALL) ? config.possibleHosts() : List.of());
        return "admin/scheduling/agenda";
    }

    @GetMapping("/appointments/{id}")
    String appointment(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, Model model) {
        Appointment appointment = booking.view(id, restriction(me));
        model.addAttribute("appointment", appointment);
        model.addAttribute("when", BookingService.when(appointment.getStartsAt()));
        model.addAttribute("past", appointment.getStartsAt().isBefore(time.now()));
        return "admin/scheduling/appointment";
    }

    @PostMapping("/appointments/{id}/attended")
    String attended(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/scheduling/appointments/" + id, "Asistencia registrada", () -> booking.markAttended(id, restriction(me)));
    }

    @PostMapping("/appointments/{id}/no-show")
    String noShow(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/scheduling/appointments/" + id, "Registrado: no asistió", () -> booking.markNoShow(id, restriction(me)));
    }

    @PostMapping("/appointments/{id}/cancel")
    String cancel(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam String reason,
                  RedirectAttributes redirect) {
        return run(redirect, "/admin/scheduling/appointments/" + id, "Cita cancelada; la familia recibió el aviso",
                () -> booking.cancelByStaff(id, reason, restriction(me)));
    }

    @PostMapping("/appointments/{id}/notes")
    String notes(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) String notes,
                 RedirectAttributes redirect) {
        return run(redirect, "/admin/scheduling/appointments/" + id, "Notas guardadas", () -> booking.saveNotes(id, notes, restriction(me)));
    }

    // --- Disponibilidad ---

    @GetMapping("/availability")
    String availability(@AuthenticationPrincipal SchoolUser me, @RequestParam(required = false) Long host, Model model) {
        long hostId = ownOrManaged(me, host);
        model.addAttribute("hostId", hostId);
        model.addAttribute("rules", config.rulesOf(hostId));
        model.addAttribute("types", config.typesHostedBy(hostId));
        model.addAttribute("blocks", config.upcomingBlocks(hostId, time.now()));
        model.addAttribute("days", DayOfWeek.values());
        model.addAttribute("manageAll", me.can(Permission.SCHEDULING_ALL));
        model.addAttribute("hosts", me.can(Permission.SCHEDULING_ALL) ? config.possibleHosts() : List.of());
        return "admin/scheduling/availability";
    }

    @PostMapping("/availability/rules")
    String addRule(@AuthenticationPrincipal SchoolUser me, @RequestParam(required = false) Long host,
                   @RequestParam(name = "day", required = false) Set<DayOfWeek> days,
                   @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime start,
                   @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime end,
                   @RequestParam(required = false) Long typeId,
                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
                   RedirectAttributes redirect) {
        long hostId = ownOrManaged(me, host);
        return run(redirect, availabilityUrl(hostId), "Disponibilidad agregada",
                () -> config.addRule(hostId, days, start, end, typeId, validFrom, validUntil));
    }

    @PostMapping("/availability/rules/{id}/delete")
    String deleteRule(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) Long host,
                      RedirectAttributes redirect) {
        long hostId = ownOrManaged(me, host);
        return run(redirect, availabilityUrl(hostId), "Disponibilidad eliminada", () -> config.deleteRule(id, hostId));
    }

    @PostMapping("/availability/blocks")
    String addBlock(@AuthenticationPrincipal SchoolUser me, @RequestParam(required = false) Long host,
                    @RequestParam(defaultValue = "false") boolean wholeSchool,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                    @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        long hostId = ownOrManaged(me, host);
        if (wholeSchool && !me.can(Permission.SCHEDULING_ALL)) {
            redirect.addFlashAttribute("problem", "Solo quien administra la agenda del colegio bloquea a todos");
            return "redirect:" + availabilityUrl(hostId);
        }
        return run(redirect, availabilityUrl(hostId), "Bloqueo agregado",
                () -> config.addBlock(wholeSchool ? null : hostId, start, end, reason));
    }

    @PostMapping("/availability/blocks/{id}/delete")
    String deleteBlock(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) Long host,
                       RedirectAttributes redirect) {
        long hostId = ownOrManaged(me, host);
        return run(redirect, availabilityUrl(hostId), "Bloqueo eliminado",
                () -> config.deleteBlock(id, me.can(Permission.SCHEDULING_ALL) ? null : hostId));
    }

    // --- Apoyo ---

    /** Sin el permiso general cada uno ve solo lo suyo; con él, lo que elija (nulo = todos). */
    private static Long hostFilter(SchoolUser me, Long requested) {
        return me.can(Permission.SCHEDULING_ALL) ? requested : Long.valueOf(me.id());
    }

    private static Long restriction(SchoolUser me) {
        return me.can(Permission.SCHEDULING_ALL) ? null : me.id();
    }

    private static long ownOrManaged(SchoolUser me, Long requested) {
        return me.can(Permission.SCHEDULING_ALL) && requested != null ? requested : me.id();
    }

    private static String availabilityUrl(long hostId) {
        return "/admin/scheduling/availability?host=" + hostId;
    }

    private static String run(RedirectAttributes redirect, String back, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:" + back;
    }
}
