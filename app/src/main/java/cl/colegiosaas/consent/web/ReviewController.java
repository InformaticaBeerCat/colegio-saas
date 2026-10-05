package cl.colegiosaas.consent.web;

import cl.colegiosaas.consent.ConsentRegistry;
import cl.colegiosaas.consent.ImageReview;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.StructureService;
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

import java.util.List;
import java.util.Set;

/** Revisión de fotos por el gestor de consentimientos (MED-06). */
@Controller
@RequestMapping("/admin/review")
@PreAuthorize("hasAuthority('MEDIA_REVIEW')")
class ReviewController {

    private final ImageReview review;
    private final MediaLibrary library;
    private final ConsentRegistry registry;
    private final StructureService structure;
    private final SchoolTime time;

    ReviewController(ImageReview review, MediaLibrary library, ConsentRegistry registry, StructureService structure, SchoolTime time) {
        this.review = review;
        this.library = library;
        this.registry = registry;
        this.structure = structure;
        this.time = time;
    }

    @GetMapping
    String queue(Model model) {
        model.addAttribute("queue", review.queue());
        return "admin/review/queue";
    }

    /** {@code curso}: curso cuyos estudiantes se muestran para etiquetar. */
    @GetMapping("/{id}")
    String show(@PathVariable long id, @RequestParam(name = "curso", required = false) Long courseId, Model model) {
        int year = time.today().getYear();
        model.addAttribute("asset", library.get(id));
        model.addAttribute("appearances", review.appearancesIn(id));
        model.addAttribute("courses", structure.courses(year));
        model.addAttribute("curso", courseId);
        model.addAttribute("candidates", courseId == null ? List.of()
                : registry.students(year).stream().filter(s -> s.active() && s.courseId() == courseId).toList());
        model.addAttribute("missingConsents", review.appearancesIn(id).stream().anyMatch(a -> !a.authorized()));
        return "admin/review/photo";
    }

    @PostMapping("/{id}/tag")
    String tag(@AuthenticationPrincipal SchoolUser me, @PathVariable long id,
               @RequestParam(name = "studentIds", required = false) Set<Long> studentIds, RedirectAttributes redirect) {
        if (studentIds != null && !studentIds.isEmpty()) {
            review.tag(id, studentIds, me.id());
            redirect.addFlashAttribute("notice", "Estudiantes etiquetados");
        }
        return "redirect:/admin/review/" + id;
    }

    @PostMapping("/{id}/untag")
    String untag(@PathVariable long id, @RequestParam long studentId) {
        review.untag(id, studentId);
        return "redirect:/admin/review/" + id;
    }

    @PostMapping("/{id}/approve")
    String approve(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) String altText,
                   @RequestParam(defaultValue = "false") boolean confirmedBlurred, RedirectAttributes redirect) {
        try {
            if (altText != null && !altText.isBlank()) {
                library.updateAltText(id, altText);
            }
            review.approve(id, confirmedBlurred, me.id());
            redirect.addFlashAttribute("notice", "Foto aprobada: ya se puede mostrar en el sitio");
            return "redirect:/admin/review";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/review/" + id;
        }
    }

    @PostMapping("/{id}/reject")
    String reject(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) String note,
                  RedirectAttributes redirect) {
        review.reject(id, note, me.id());
        redirect.addFlashAttribute("notice", "Foto rechazada: no se mostrará en el sitio");
        return "redirect:/admin/review";
    }

    @PostMapping("/{id}/exempt")
    String exempt(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) String altText,
                  RedirectAttributes redirect) {
        try {
            if (altText != null && !altText.isBlank()) {
                library.updateAltText(id, altText);
            }
            review.exempt(id, me.id());
            redirect.addFlashAttribute("notice", "Marcada como foto sin personas");
            return "redirect:/admin/review";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/review/" + id;
        }
    }
}
