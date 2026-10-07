package cl.colegiosaas.admissions.web;

import cl.colegiosaas.admissions.AdmissionMode;
import cl.colegiosaas.admissions.AdmissionService;
import cl.colegiosaas.admissions.ProspectStage;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Features;
import cl.colegiosaas.platform.RequiresFeature;
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

/** Admisión en el panel: proceso, hitos, niveles (vacantes y edades) y registros de interés. */
@Controller
@RequestMapping("/admin/admissions")
@RequiresFeature(Feature.SAE_ADMISSIONS)
@PreAuthorize("hasAuthority('ADMISSIONS')")
class AdmissionAdminController {

    private final AdmissionService admissions;
    private final Features features;

    AdmissionAdminController(AdmissionService admissions, Features features) {
        this.admissions = admissions;
        this.features = features;
    }

    @GetMapping
    String overview(Model model) {
        model.addAttribute("settings", admissions.current());
        model.addAttribute("levels", admissions.levels());
        model.addAttribute("milestones", admissions.milestones());
        model.addAttribute("modes", AdmissionMode.values());
        model.addAttribute("ownAllowed", features.on(Feature.OWN_ADMISSIONS));
        return "admin/admissions/overview";
    }

    @PostMapping("/settings")
    String settings(@RequestParam(required = false) AdmissionMode mode, @RequestParam int processYear,
                    @RequestParam(required = false) String saeUrl, @RequestParam(required = false) String introText,
                    @RequestParam(defaultValue = "false") boolean showVacancies, RedirectAttributes redirect) {
        return run(redirect, "/admin/admissions", "Proceso guardado",
                () -> admissions.updateSettings(mode, processYear, saeUrl, introText, showVacancies));
    }

    @PostMapping("/levels/{id}")
    String level(@PathVariable long id, @RequestParam(defaultValue = "false") boolean open,
                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bornFrom,
                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bornUntil,
                 @RequestParam(required = false) Integer seats, RedirectAttributes redirect) {
        return run(redirect, "/admin/admissions#niveles", "Nivel guardado",
                () -> admissions.saveLevel(id, open, bornFrom, bornUntil, seats));
    }

    @PostMapping("/milestones")
    String addMilestone(@RequestParam String name,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn,
                        @RequestParam(required = false) String description, @RequestParam(required = false) String linkUrl,
                        RedirectAttributes redirect) {
        return run(redirect, "/admin/admissions#hitos", "Hito agregado",
                () -> admissions.addMilestone(name, startsOn, endsOn, description, linkUrl));
    }

    @PostMapping("/milestones/{id}/delete")
    String deleteMilestone(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/admissions#hitos", "Hito eliminado", () -> admissions.deleteMilestone(id));
    }

    @GetMapping("/prospects")
    String prospects(Model model) {
        model.addAttribute("prospects", admissions.prospects());
        return "admin/admissions/prospects";
    }

    @GetMapping("/prospects/{id}")
    String prospect(@PathVariable long id, Model model) {
        model.addAttribute("prospect", admissions.prospect(id));
        model.addAttribute("stages", ProspectStage.values());
        return "admin/admissions/prospect";
    }

    @PostMapping("/prospects/{id}/stage")
    String stage(@PathVariable long id, @RequestParam ProspectStage stage, RedirectAttributes redirect) {
        return run(redirect, "/admin/admissions/prospects/" + id, "Etapa actualizada", () -> admissions.advance(id, stage));
    }

    @PostMapping("/prospects/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/admissions/prospects", "Registro eliminado", () -> admissions.delete(id));
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
