package cl.colegiosaas.documents.web;

import cl.colegiosaas.documents.DocumentCategory;
import cl.colegiosaas.documents.DocumentService;
import cl.colegiosaas.documents.InstitutionalDocument;
import cl.colegiosaas.media.DocumentUploads;
import cl.colegiosaas.media.FileUploadException;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/** Documentos institucionales en el panel (DOC-01..05). */
@Controller
@RequestMapping("/admin/documents")
@PreAuthorize("hasAuthority('DOCUMENTS')")
class DocumentAdminController {

    private final DocumentService documents;
    private final DocumentUploads uploads;
    private final SchoolRepository schools;
    private final SchoolTime time;

    DocumentAdminController(DocumentService documents, DocumentUploads uploads, SchoolRepository schools, SchoolTime time) {
        this.documents = documents;
        this.uploads = uploads;
        this.schools = schools;
        this.time = time;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("documents", documents.list());
        model.addAttribute("categories", DocumentCategory.values());
        model.addAttribute("regulations", documents.regulations());
        model.addAttribute("today", time.today());
        return "admin/documents/list";
    }

    @PostMapping
    String create(@RequestParam DocumentCategory category, @RequestParam String title,
                  @RequestParam(required = false) String description, @RequestParam(required = false) Long parentId,
                  RedirectAttributes redirect) {
        try {
            InstitutionalDocument document = documents.create(category, title, description, parentId);
            redirect.addFlashAttribute("notice", "Documento creado. Publica su primera versión en PDF.");
            return "redirect:/admin/documents/" + document.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/documents";
        }
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        InstitutionalDocument document = documents.get(id);
        model.addAttribute("document", document);
        model.addAttribute("regulations", documents.regulations().stream().filter(d -> !d.getId().equals(id)).toList());
        model.addAttribute("today", time.today());
        model.addAttribute("missingRbd", document.getCategory().requiresRegulatoryMetadata()
                && schools.findSingleton().map(s -> s.getRbd() == null).orElse(true));
        return "admin/documents/edit";
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @RequestParam String title, @RequestParam(required = false) String description,
                  @RequestParam(required = false) Long parentId, @RequestParam(defaultValue = "0") int sortOrder,
                  RedirectAttributes redirect) {
        return run(redirect, id, "Datos del documento guardados",
                () -> documents.update(id, title, description, parentId, sortOrder));
    }

    @PostMapping("/{id}/versions")
    String publishVersion(@AuthenticationPrincipal SchoolUser me, @PathVariable long id,
                          @RequestParam("file") MultipartFile file, @RequestParam(required = false) Integer academicYear,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastUpdatedOn,
                          @RequestParam(defaultValue = "false") boolean accessiblePdf,
                          @RequestParam(required = false) String changeNotes, RedirectAttributes redirect) {
        try {
            documents.publishVersion(id, uploads.storePdf(file), academicYear, lastUpdatedOn, accessiblePdf, changeNotes, me.id());
            redirect.addFlashAttribute("notice", "Versión publicada; la anterior quedó en el historial");
        } catch (RuleViolation | FileUploadException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/documents/" + id;
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            documents.delete(id);
            redirect.addFlashAttribute("notice", "Documento eliminado");
            return "redirect:/admin/documents";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/documents/" + id;
        }
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/documents/" + id;
    }
}
