package cl.colegiosaas.info.web;

import cl.colegiosaas.info.GeneralInfoService;
import cl.colegiosaas.info.InfoSheet;
import cl.colegiosaas.info.InfoSheetDraft;
import cl.colegiosaas.info.InfoSheetKind;
import cl.colegiosaas.info.Workshop;
import cl.colegiosaas.info.WorkshopDraft;
import cl.colegiosaas.media.DocumentUploads;
import cl.colegiosaas.media.FileUploadException;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/** Preguntas frecuentes, talleres y útiles/uniforme/minuta en el panel (PUB-06, PUB-09, PUB-10). */
@Controller
@RequestMapping("/admin/info")
@PreAuthorize("hasAuthority('GENERAL_INFO')")
class InfoAdminController {

    private final GeneralInfoService info;
    private final DocumentUploads uploads;
    private final GradeLevelRepository gradeLevels;
    private final SchoolTime time;

    InfoAdminController(GeneralInfoService info, DocumentUploads uploads, GradeLevelRepository gradeLevels, SchoolTime time) {
        this.info = info;
        this.uploads = uploads;
        this.gradeLevels = gradeLevels;
        this.time = time;
    }

    // --- Preguntas frecuentes ---

    @GetMapping("/faq")
    String faq(Model model) {
        model.addAttribute("categories", info.faqCategories());
        model.addAttribute("entries", info.allFaqEntries());
        return "admin/info/faq";
    }

    @PostMapping("/faq/categories")
    String addCategory(@RequestParam String name, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/faq", "Categoría agregada", () -> info.addFaqCategory(name));
    }

    @PostMapping("/faq/categories/{id}/delete")
    String deleteCategory(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/faq", "Categoría eliminada", () -> info.deleteFaqCategory(id));
    }

    @PostMapping("/faq")
    String addEntry(@RequestParam long categoryId, @RequestParam String question, @RequestParam String answer,
                    @RequestParam(defaultValue = "false") boolean published, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/faq", "Pregunta agregada",
                () -> info.saveFaqEntry(null, categoryId, question, answer, published));
    }

    @GetMapping("/faq/{id}")
    String editEntry(@PathVariable long id, Model model) {
        model.addAttribute("entry", info.faqEntry(id));
        model.addAttribute("categories", info.faqCategories());
        return "admin/info/faq-entry";
    }

    @PostMapping("/faq/{id}")
    String updateEntry(@PathVariable long id, @RequestParam long categoryId, @RequestParam String question,
                       @RequestParam String answer, @RequestParam(defaultValue = "false") boolean published,
                       RedirectAttributes redirect) {
        return run(redirect, "/admin/info/faq", "Pregunta guardada",
                () -> info.saveFaqEntry(id, categoryId, question, answer, published));
    }

    @PostMapping("/faq/{id}/delete")
    String deleteEntry(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/faq", "Pregunta eliminada", () -> info.deleteFaqEntry(id));
    }

    // --- Talleres ---

    @GetMapping("/workshops")
    String workshops(Model model) {
        model.addAttribute("workshops", info.workshops());
        return "admin/info/workshops";
    }

    @GetMapping({"/workshops/new", "/workshops/{id}"})
    String editWorkshop(@PathVariable(required = false) Long id, Model model) {
        Workshop workshop = id == null ? null : info.workshop(id);
        model.addAttribute("workshop", workshop);
        model.addAttribute("selectedLevels", workshop == null ? Set.of() : ids(workshop.getGradeLevels()));
        model.addAttribute("defaultYear", time.today().getYear());
        model.addAttribute("gradeLevels", gradeLevels.findAllByOrderBySortOrderAsc());
        return "admin/info/workshop";
    }

    @PostMapping({"/workshops", "/workshops/{id}"})
    String saveWorkshop(@PathVariable(required = false) Long id, @RequestParam String name,
                        @RequestParam(required = false) String description, @RequestParam(required = false) String schedule,
                        @RequestParam(required = false) String instructor, @RequestParam(required = false) Integer capacity,
                        @RequestParam int academicYear, @RequestParam(defaultValue = "false") boolean active,
                        @RequestParam(required = false) Set<Long> gradeLevelIds, RedirectAttributes redirect) {
        WorkshopDraft draft = new WorkshopDraft(name, description, schedule, instructor, capacity, academicYear, active, gradeLevelIds);
        try {
            Workshop workshop = info.saveWorkshop(id, draft);
            redirect.addFlashAttribute("notice", "Taller guardado");
            return "redirect:/admin/info/workshops/" + workshop.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/info/workshops" + (id == null ? "/new" : "/" + id);
        }
    }

    @PostMapping("/workshops/{id}/delete")
    String deleteWorkshop(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/workshops", "Taller eliminado", () -> info.deleteWorkshop(id));
    }

    // --- Útiles, uniforme y minuta ---

    @GetMapping("/sheets")
    String sheets(Model model) {
        model.addAttribute("sheets", info.sheets());
        return "admin/info/sheets";
    }

    @GetMapping({"/sheets/new", "/sheets/{id}"})
    String editSheet(@PathVariable(required = false) Long id, Model model) {
        model.addAttribute("sheet", id == null ? null : info.sheet(id));
        model.addAttribute("kinds", InfoSheetKind.values());
        model.addAttribute("defaultYear", time.today().getYear());
        model.addAttribute("gradeLevels", gradeLevels.findAllByOrderBySortOrderAsc());
        return "admin/info/sheet";
    }

    @PostMapping({"/sheets", "/sheets/{id}"})
    String saveSheet(@PathVariable(required = false) Long id, @RequestParam InfoSheetKind kind, @RequestParam String title,
                     @RequestParam(required = false) Long gradeLevelId, @RequestParam int academicYear,
                     @RequestParam(required = false) String content,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
                     RedirectAttributes redirect) {
        try {
            InfoSheet sheet = info.saveSheet(id, new InfoSheetDraft(kind, title, gradeLevelId, academicYear, content, validFrom, validUntil));
            redirect.addFlashAttribute("notice", "Ficha guardada");
            return "redirect:/admin/info/sheets/" + sheet.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/info/sheets" + (id == null ? "/new" : "/" + id);
        }
    }

    @PostMapping("/sheets/{id}/file")
    String attachSheetFile(@PathVariable long id, @RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        try {
            info.attachSheetFile(id, uploads.storePdf(file));
            redirect.addFlashAttribute("notice", "PDF adjunto");
        } catch (FileUploadException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/info/sheets/" + id;
    }

    @PostMapping("/sheets/{id}/file/delete")
    String removeSheetFile(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/sheets/" + id, "PDF quitado", () -> info.removeSheetFile(id));
    }

    @PostMapping("/sheets/{id}/publish")
    String publishSheet(@PathVariable long id, @RequestParam boolean published, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/sheets/" + id, published ? "Ficha publicada" : "Ficha retirada del sitio",
                () -> info.publishSheet(id, published));
    }

    @PostMapping("/sheets/{id}/delete")
    String deleteSheet(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/info/sheets", "Ficha eliminada", () -> info.deleteSheet(id));
    }

    private static Set<Long> ids(Set<? extends BaseEntity> entities) {
        return entities.stream().map(BaseEntity::getId).collect(Collectors.toSet());
    }

    private static String run(RedirectAttributes redirect, String target, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:" + target;
    }
}
