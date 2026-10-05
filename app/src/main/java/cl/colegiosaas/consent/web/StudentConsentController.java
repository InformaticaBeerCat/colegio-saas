package cl.colegiosaas.consent.web;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.consent.ConsentChannel;
import cl.colegiosaas.consent.ConsentMethod;
import cl.colegiosaas.consent.ConsentRegistry;
import cl.colegiosaas.consent.Student;
import cl.colegiosaas.media.DocumentUploads;
import cl.colegiosaas.media.FileUploadException;
import cl.colegiosaas.media.StoredFile;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.Map;

/**
 * Estudiantes y autorizaciones de imagen. Ver nombres de menores es acceso a datos personales: cada
 * consulta del detalle queda en la auditoría.
 */
@Controller
@RequestMapping("/admin/students")
@PreAuthorize("hasAuthority('STUDENTS_AND_CONSENTS')")
class StudentConsentController {

    private final ConsentRegistry registry;
    private final StructureService structure;
    private final DocumentUploads uploads;
    private final SchoolTime time;
    private final AuditTrail audit;

    StudentConsentController(ConsentRegistry registry, StructureService structure, DocumentUploads uploads, SchoolTime time,
                             AuditTrail audit) {
        this.registry = registry;
        this.structure = structure;
        this.uploads = uploads;
        this.time = time;
        this.audit = audit;
    }

    @GetMapping
    String list(@RequestParam(name = "anio", required = false) Integer year, Model model) {
        int academicYear = year == null ? time.today().getYear() : year;
        model.addAttribute("year", academicYear);
        model.addAttribute("students", registry.students(academicYear));
        model.addAttribute("courses", structure.courses(academicYear));
        model.addAttribute("form", registry.currentForm().orElse(null));
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "Student", null, "Lista de estudiantes " + academicYear);
        return "admin/students/list";
    }

    @PostMapping("/form")
    String publishForm(@AuthenticationPrincipal SchoolUser me, RedirectAttributes redirect) {
        try {
            registry.publishBaseForm(me.id());
            redirect.addFlashAttribute("notice", "Formulario de autorización publicado. Revísalo con la asesoría legal del colegio.");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/students";
    }

    @PostMapping
    String add(@RequestParam String fullName, @RequestParam long courseId, @RequestParam(required = false) String guardianEmail,
               RedirectAttributes redirect) {
        try {
            Student student = registry.addStudent(fullName, courseId, guardianEmail);
            redirect.addFlashAttribute("notice", "Estudiante agregado");
            return "redirect:/admin/students/" + student.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/students";
        }
    }

    @GetMapping("/{id}")
    String show(@PathVariable long id, Model model) {
        Student student = registry.student(id);
        Map<ConsentChannel, Boolean> active = new EnumMap<>(ConsentChannel.class);
        registry.history(id).forEach(c -> active.merge(c.getChannel(), c.isActive(), Boolean::logicalOr));
        model.addAttribute("student", student);
        model.addAttribute("history", registry.history(id));
        model.addAttribute("active", active);
        model.addAttribute("photos", registry.photosOf(id));
        model.addAttribute("channels", ConsentChannel.values());
        model.addAttribute("methods", ConsentMethod.values());
        model.addAttribute("form", registry.currentForm().orElse(null));
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "Student", id, "Autorizaciones de imagen");
        return "admin/students/show";
    }

    @PostMapping("/{id}/consents")
    String grant(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam ConsentChannel channel,
                 @RequestParam String grantedByName, @RequestParam ConsentMethod method,
                 @RequestParam(name = "evidence", required = false) MultipartFile evidence, RedirectAttributes redirect) {
        try {
            StoredFile file = evidence == null || evidence.isEmpty() ? null : uploads.storePdf(evidence);
            registry.grant(id, channel, grantedByName, method, file, me.id());
            redirect.addFlashAttribute("notice", "Autorización registrada");
        } catch (RuleViolation | FileUploadException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/students/" + id;
    }

    @PostMapping("/{id}/consents/revoke")
    String revoke(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam ConsentChannel channel,
                  @RequestParam(required = false) String note, RedirectAttributes redirect) {
        try {
            int withdrawn = registry.revoke(id, channel, note, me.id());
            redirect.addFlashAttribute("notice", withdrawn > 0
                    ? "Autorización revocada; " + withdrawn + " foto(s) retirada(s) del sitio"
                    : "Autorización revocada");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/students/" + id;
    }

    @PostMapping("/{id}/deactivate")
    String deactivate(@PathVariable long id, RedirectAttributes redirect) {
        registry.deactivate(id);
        redirect.addFlashAttribute("notice", "Estudiante marcado como retirado del colegio");
        return "redirect:/admin/students/" + id;
    }
}
