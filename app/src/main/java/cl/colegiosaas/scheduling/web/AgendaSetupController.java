package cl.colegiosaas.scheduling.web;

import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.scheduling.AgendaConfigService;
import cl.colegiosaas.scheduling.AppointmentAudience;
import cl.colegiosaas.scheduling.AppointmentType;
import cl.colegiosaas.scheduling.HolidayImporter;
import cl.colegiosaas.shared.web.RuleViolation;
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

import java.time.LocalDate;
import java.util.Set;

/** Configuración general de la agenda: tipos de cita (AGE-01) y feriados (AGE-02). */
@Controller
@RequestMapping("/admin/scheduling")
@RequiresFeature(Feature.SCHEDULING)
@PreAuthorize("hasAuthority('SCHEDULING_ALL')")
class AgendaSetupController {

    private final AgendaConfigService config;
    private final HolidayImporter importer;
    private final SchoolTime time;

    AgendaSetupController(AgendaConfigService config, HolidayImporter importer, SchoolTime time) {
        this.config = config;
        this.importer = importer;
        this.time = time;
    }

    @GetMapping("/types")
    String types(Model model) {
        model.addAttribute("types", config.allTypes());
        return "admin/scheduling/types";
    }

    @GetMapping("/types/new")
    String newType(Model model) {
        return editor(model, null);
    }

    @GetMapping("/types/{id}")
    String editType(@PathVariable long id, Model model) {
        return editor(model, config.type(id));
    }

    @PostMapping("/types")
    String create(@RequestParam String name, @RequestParam(required = false) String description,
                  @RequestParam(defaultValue = "30") int durationMinutes, @RequestParam(defaultValue = "0") int bufferMinutes,
                  @RequestParam(required = false) AppointmentAudience audience, @RequestParam(defaultValue = "false") boolean inPerson,
                  @RequestParam(defaultValue = "false") boolean online, @RequestParam(defaultValue = "false") boolean active,
                  @RequestParam(name = "hostIds", required = false) Set<Long> hostIds, RedirectAttributes redirect) {
        try {
            AppointmentType type = config.saveType(null, new AgendaConfigService.TypeDraft(name, description, durationMinutes,
                    bufferMinutes, audience, inPerson, online, active, hostIds));
            redirect.addFlashAttribute("notice", "Tipo de cita creado");
            return "redirect:/admin/scheduling/types/" + type.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/scheduling/types/new";
        }
    }

    @PostMapping("/types/{id}")
    String update(@PathVariable long id, @RequestParam String name, @RequestParam(required = false) String description,
                  @RequestParam(defaultValue = "30") int durationMinutes, @RequestParam(defaultValue = "0") int bufferMinutes,
                  @RequestParam(required = false) AppointmentAudience audience, @RequestParam(defaultValue = "false") boolean inPerson,
                  @RequestParam(defaultValue = "false") boolean online, @RequestParam(defaultValue = "false") boolean active,
                  @RequestParam(name = "hostIds", required = false) Set<Long> hostIds, RedirectAttributes redirect) {
        return run(redirect, "/admin/scheduling/types/" + id, "Tipo de cita guardado", () -> config.saveType(id,
                new AgendaConfigService.TypeDraft(name, description, durationMinutes, bufferMinutes, audience, inPerson, online,
                        active, hostIds)));
    }

    @PostMapping("/types/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            config.deleteType(id);
            redirect.addFlashAttribute("notice", "Tipo de cita eliminado");
            return "redirect:/admin/scheduling/types";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/scheduling/types/" + id;
        }
    }

    // --- Feriados ---

    @GetMapping("/holidays")
    String holidays(@RequestParam(name = "anio", required = false) Integer year, Model model) {
        int shown = year != null ? year : time.today().getYear();
        model.addAttribute("year", shown);
        model.addAttribute("holidays", config.holidays(shown));
        return "admin/scheduling/holidays";
    }

    @PostMapping("/holidays")
    String addHoliday(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(required = false) String name, @RequestParam(defaultValue = "false") boolean mandatory,
                      RedirectAttributes redirect) {
        int year = date != null ? date.getYear() : time.today().getYear();
        return run(redirect, "/admin/scheduling/holidays?anio=" + year, "Feriado agregado", () -> config.addHoliday(date, name, mandatory));
    }

    @PostMapping("/holidays/import")
    String importHolidays(@RequestParam int year, RedirectAttributes redirect) {
        try {
            int added = config.importHolidays(importer.fetch(year));
            redirect.addFlashAttribute("notice", added == 0 ? "Los feriados de " + year + " ya estaban cargados"
                    : added + " feriado(s) de " + year + " agregados desde la fuente oficial");
        } catch (HolidayImporter.ImportFailed e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/scheduling/holidays?anio=" + year;
    }

    @PostMapping("/holidays/{id}/delete")
    String deleteHoliday(@PathVariable long id, @RequestParam int year, RedirectAttributes redirect) {
        return run(redirect, "/admin/scheduling/holidays?anio=" + year, "Feriado eliminado", () -> config.deleteHoliday(id));
    }

    private String editor(Model model, AppointmentType type) {
        model.addAttribute("type", type);
        model.addAttribute("audiences", AppointmentAudience.values());
        model.addAttribute("hosts", config.possibleHosts());
        return "admin/scheduling/type";
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
