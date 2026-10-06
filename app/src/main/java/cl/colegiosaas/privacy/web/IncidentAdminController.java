package cl.colegiosaas.privacy.web;

import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.IncidentService;
import cl.colegiosaas.privacy.IncidentSeverity;
import cl.colegiosaas.privacy.SecurityIncident;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Registro y seguimiento de brechas de seguridad (PRV-09). */
@Controller
@RequestMapping("/admin/privacy/incidents")
@PreAuthorize("hasAuthority('PRIVACY')")
class IncidentAdminController {

    private final IncidentService incidents;
    private final SchoolTime time;

    IncidentAdminController(IncidentService incidents, SchoolTime time) {
        this.incidents = incidents;
        this.time = time;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("incidents", incidents.list());
        model.addAttribute("severities", IncidentSeverity.values());
        model.addAttribute("now", time.now().truncatedTo(ChronoUnit.MINUTES));
        model.addAttribute("hours", incidents.notificationHours());
        return "admin/privacy/incidents";
    }

    @PostMapping
    String register(@AuthenticationPrincipal SchoolUser me, @RequestParam String title,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime detectedAt,
                    @RequestParam(required = false) IncidentSeverity severity,
                    @RequestParam(required = false) String affectedData,
                    @RequestParam(required = false) Integer affectedSubjectsEstimate,
                    @RequestParam(required = false) String description, RedirectAttributes redirect) {
        try {
            SecurityIncident incident = incidents.register(new IncidentService.Details(title, detectedAt, severity,
                    affectedData, affectedSubjectsEstimate, description, null), me.id());
            redirect.addFlashAttribute("notice", "Incidente registrado. Sigue los pasos del procedimiento.");
            return "redirect:/admin/privacy/incidents/" + incident.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/privacy/incidents";
        }
    }

    @GetMapping("/{id}")
    String show(@PathVariable long id, Model model) {
        SecurityIncident incident = incidents.get(id);
        model.addAttribute("incident", incident);
        model.addAttribute("clock", incidents.clockwork(incident));
        model.addAttribute("hours", incidents.notificationHours());
        model.addAttribute("severities", IncidentSeverity.values());
        model.addAttribute("detectedLocal", time.toLocal(incident.getDetectedAt()));
        model.addAttribute("now", time.now().truncatedTo(ChronoUnit.MINUTES));
        model.addAttribute("authorityNotice", incidents.authorityNotice(incident));
        model.addAttribute("subjectsNotice", incidents.subjectsNotice(incident));
        return "admin/privacy/incident";
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @RequestParam String title,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime detectedAt,
                  @RequestParam(required = false) IncidentSeverity severity,
                  @RequestParam(required = false) String affectedData,
                  @RequestParam(required = false) Integer affectedSubjectsEstimate,
                  @RequestParam(required = false) String description,
                  @RequestParam(required = false) String actionsTaken, RedirectAttributes redirect) {
        return run(redirect, id, "Incidente actualizado", () -> incidents.update(id, new IncidentService.Details(title,
                detectedAt, severity, affectedData, affectedSubjectsEstimate, description, actionsTaken)));
    }

    @PostMapping("/{id}/contain")
    String contain(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Marcado como contenido", () -> incidents.contain(id));
    }

    @PostMapping("/{id}/authority")
    String authority(@PathVariable long id,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                     RedirectAttributes redirect) {
        return run(redirect, id, "Notificación a la Agencia registrada", () -> incidents.recordAuthorityNotification(id, at));
    }

    @PostMapping("/{id}/subjects")
    String subjects(@PathVariable long id,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
                    RedirectAttributes redirect) {
        return run(redirect, id, "Aviso a los titulares registrado", () -> incidents.recordSubjectsNotification(id, at));
    }

    @PostMapping("/{id}/close")
    String close(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Incidente cerrado", () -> incidents.close(id));
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/privacy/incidents/" + id;
    }
}
