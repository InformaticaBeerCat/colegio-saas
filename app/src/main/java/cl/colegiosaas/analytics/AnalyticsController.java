package cl.colegiosaas.analytics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Visitas del sitio en el panel (REP-01). */
@Controller
@PreAuthorize("hasAnyAuthority('SCHOOL_SETTINGS', 'PAGES')")
class AnalyticsController {

    private final AnalyticsService analytics;
    private final AnalyticsProperties properties;

    AnalyticsController(AnalyticsService analytics, AnalyticsProperties properties) {
        this.analytics = analytics;
        this.properties = properties;
    }

    @GetMapping("/admin/analytics")
    String report(@RequestParam(name = "dias", defaultValue = "30") int days, Model model) {
        int range = days == 7 || days == 90 ? days : 30;
        model.addAttribute("report", analytics.report(range));
        model.addAttribute("range", range);
        model.addAttribute("enabled", properties.enabled());
        model.addAttribute("external", properties.hasExternalScript());
        return "admin/analytics";
    }
}
