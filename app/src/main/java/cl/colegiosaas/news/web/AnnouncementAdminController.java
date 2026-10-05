package cl.colegiosaas.news.web;

import cl.colegiosaas.media.DocumentUploads;
import cl.colegiosaas.media.FileUploadException;
import cl.colegiosaas.news.Announcement;
import cl.colegiosaas.news.AnnouncementAudience;
import cl.colegiosaas.news.AnnouncementService;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.CourseRepository;
import cl.colegiosaas.structure.GradeLevelRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Comunicados y circulares (NOT-03). */
@Controller
@RequestMapping("/admin/announcements")
@PreAuthorize("hasAuthority('ANNOUNCEMENTS')")
class AnnouncementAdminController {

    private final AnnouncementService announcements;
    private final DocumentUploads uploads;
    private final GradeLevelRepository gradeLevels;
    private final CourseRepository courses;
    private final SchoolTime time;

    AnnouncementAdminController(AnnouncementService announcements, DocumentUploads uploads,
                                GradeLevelRepository gradeLevels, CourseRepository courses, SchoolTime time) {
        this.announcements = announcements;
        this.uploads = uploads;
        this.gradeLevels = gradeLevels;
        this.courses = courses;
        this.time = time;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("announcements", announcements.list());
        return "admin/announcements/list";
    }

    @PostMapping
    String create(@AuthenticationPrincipal SchoolUser me, @RequestParam String title, RedirectAttributes redirect) {
        try {
            Announcement announcement = announcements.create(title, me.id());
            return "redirect:/admin/announcements/" + announcement.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/announcements";
        }
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        Announcement announcement = announcements.get(id);
        return show(announcement, AnnouncementForm.of(announcement), model);
    }

    @PostMapping("/{id}")
    String save(@PathVariable long id, @Valid @ModelAttribute("form") AnnouncementForm form, BindingResult errors,
                Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                announcements.update(id, form.toDraft());
                redirect.addFlashAttribute("notice", "Comunicado guardado");
                return "redirect:/admin/announcements/" + id;
            } catch (RuleViolation e) {
                errors.reject("announcement", e.getMessage());
            }
        }
        return show(announcements.get(id), form, model);
    }

    @PostMapping("/{id}/attachment")
    String attach(@PathVariable long id, @RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        try {
            announcements.attach(id, uploads.storePdf(file));
            redirect.addFlashAttribute("notice", "Circular adjunta");
        } catch (FileUploadException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/announcements/" + id;
    }

    @PostMapping("/{id}/attachment/delete")
    String removeAttachment(@PathVariable long id, RedirectAttributes redirect) {
        announcements.removeAttachment(id);
        redirect.addFlashAttribute("notice", "Adjunto quitado");
        return "redirect:/admin/announcements/" + id;
    }

    @PostMapping("/{id}/publish")
    String publish(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/announcements/" + id, "Comunicado publicado", () -> announcements.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    String unpublish(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, "/admin/announcements/" + id, "Comunicado retirado del sitio", () -> announcements.unpublish(id));
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            announcements.delete(id);
            redirect.addFlashAttribute("notice", "Comunicado eliminado");
            return "redirect:/admin/announcements";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/announcements/" + id;
        }
    }

    private String show(Announcement announcement, AnnouncementForm form, Model model) {
        model.addAttribute("announcement", announcement);
        model.addAttribute("form", form);
        model.addAttribute("audiences", AnnouncementAudience.values());
        model.addAttribute("gradeLevels", gradeLevels.findAllByOrderBySortOrderAsc());
        model.addAttribute("courses", courses.findByAcademicYearOrderByGradeLevel_SortOrderAscSectionAsc(time.today().getYear()));
        return "admin/announcements/edit";
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
