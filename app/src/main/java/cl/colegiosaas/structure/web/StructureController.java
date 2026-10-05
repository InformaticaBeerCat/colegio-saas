package cl.colegiosaas.structure.web;

import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.structure.StructureService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Year;

/** Niveles y cursos del colegio. */
@Controller
@RequestMapping("/admin/structure")
@PreAuthorize("hasAuthority('SCHOOL_SETTINGS')")
class StructureController {

    private final StructureService structure;
    private final Clock clock;

    StructureController(StructureService structure, Clock clock) {
        this.structure = structure;
        this.clock = clock;
    }

    @GetMapping
    String show(@RequestParam(required = false) Integer year, Model model) {
        int academicYear = year == null ? Year.now(clock).getValue() : year;
        model.addAttribute("levels", structure.levels());
        model.addAttribute("courses", structure.courses(academicYear));
        model.addAttribute("year", academicYear);
        model.addAttribute("stages", EducationStage.values());
        return "admin/structure/index";
    }

    @PostMapping("/levels/standard")
    String loadStandard(RedirectAttributes redirect) {
        structure.loadChileanLevels();
        redirect.addFlashAttribute("notice", "Niveles cargados. Elimina los que el colegio no imparte.");
        return "redirect:/admin/structure";
    }

    @PostMapping("/levels")
    String addLevel(@RequestParam String name, @RequestParam EducationStage stage, RedirectAttributes redirect) {
        return run(redirect, "Nivel agregado", null, () -> structure.addLevel(name, stage));
    }

    @PostMapping("/levels/{id}/delete")
    String deleteLevel(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "Nivel eliminado", null, () -> structure.deleteLevel(id));
    }

    @PostMapping("/courses")
    String addCourse(@RequestParam long levelId, @RequestParam(defaultValue = "") String section,
                     @RequestParam int year, RedirectAttributes redirect) {
        return run(redirect, "Curso agregado", year, () -> structure.addCourse(levelId, section, year));
    }

    @PostMapping("/courses/{id}/delete")
    String deleteCourse(@PathVariable long id, @RequestParam int year, RedirectAttributes redirect) {
        return run(redirect, "Curso eliminado", year, () -> structure.deleteCourse(id));
    }

    private static String run(RedirectAttributes redirect, String success, Integer year, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/structure" + (year == null ? "" : "?year=" + year);
    }
}
