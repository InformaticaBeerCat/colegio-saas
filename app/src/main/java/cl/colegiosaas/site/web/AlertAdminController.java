package cl.colegiosaas.site.web;

import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.site.AlertSeverity;
import cl.colegiosaas.site.SiteAlertService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/** Avisos urgentes en todo el sitio (PUB-12). Los maneja quien publica comunicados. */
@Controller
@RequestMapping("/admin/alerts")
@PreAuthorize("hasAuthority('ANNOUNCEMENTS')")
class AlertAdminController {

    private final SiteAlertService alerts;
    private final SchoolTime time;

    AlertAdminController(SiteAlertService alerts, SchoolTime time) {
        this.alerts = alerts;
        this.time = time;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("alerts", alerts.list());
        model.addAttribute("visible", alerts.visibleNow());
        model.addAttribute("severities", AlertSeverity.values());
        return "admin/alerts";
    }

    /** Fechas en hora del colegio. */
    @PostMapping
    String create(@RequestParam String message, @RequestParam AlertSeverity severity,
                  @RequestParam(required = false) String linkUrl, @RequestParam(required = false) String linkLabel,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
                  @RequestParam(defaultValue = "false") boolean activateNow, RedirectAttributes redirect) {
        try {
            alerts.create(message, severity, linkUrl, linkLabel, time.toInstant(startsAt), time.toInstant(endsAt), activateNow);
            redirect.addFlashAttribute("notice", activateNow ? "Aviso activado en todo el sitio" : "Aviso guardado (inactivo)");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/alerts";
    }

    @PostMapping("/{id}/active")
    String activate(@PathVariable long id, @RequestParam boolean active, RedirectAttributes redirect) {
        alerts.activate(id, active);
        redirect.addFlashAttribute("notice", active ? "Aviso activado" : "Aviso desactivado");
        return "redirect:/admin/alerts";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        alerts.delete(id);
        redirect.addFlashAttribute("notice", "Aviso eliminado");
        return "redirect:/admin/alerts";
    }
}
