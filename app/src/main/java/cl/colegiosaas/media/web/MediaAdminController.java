package cl.colegiosaas.media.web;

import cl.colegiosaas.media.FileUploadException;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaDetails;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.media.ReviewStatus;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Biblioteca de medios (MED-01..03, MED-07, MED-09, MED-10). */
@Controller
@RequestMapping("/admin/media")
@PreAuthorize("hasAnyAuthority('MEDIA_UPLOAD', 'MEDIA_REVIEW')")
class MediaAdminController {

    private final MediaLibrary library;

    MediaAdminController(MediaLibrary library) {
        this.library = library;
    }

    @GetMapping
    String list(@RequestParam(name = "estado", required = false) ReviewStatus status,
                @RequestParam(name = "carpeta", required = false) Long folderId,
                @RequestParam(name = "etiqueta", required = false) String tag, Model model) {
        model.addAttribute("assets", library.list(status, folderId, tag));
        model.addAttribute("folders", library.folders());
        model.addAttribute("statuses", ReviewStatus.values());
        model.addAttribute("estado", status);
        model.addAttribute("carpeta", folderId);
        model.addAttribute("etiqueta", tag);
        return "admin/media/list";
    }

    /** Subida masiva (MED-03): el resultado de cada archivo vuelve en el aviso. */
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('MEDIA_UPLOAD')")
    String upload(@AuthenticationPrincipal SchoolUser me, @RequestParam("files") List<MultipartFile> files,
                  @RequestParam(required = false) Long folderId, @RequestParam(required = false) String tags,
                  RedirectAttributes redirect) {
        try {
            List<MediaLibrary.UploadResult> results = library.uploadImages(read(files), me.id(), folderId, tagSet(tags));
            long ok = results.stream().filter(MediaLibrary.UploadResult::ok).count();
            if (ok > 0) {
                redirect.addFlashAttribute("notice", ok + " foto(s) subida(s). Quedan pendientes de revisión de autorización de imagen.");
            }
            List<String> failed = results.stream().filter(r -> !r.ok()).map(r -> r.name() + ": " + r.problem()).toList();
            if (!failed.isEmpty()) {
                redirect.addFlashAttribute("problem", String.join(" · ", failed));
            }
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/media";
    }

    @PostMapping("/embed")
    @PreAuthorize("hasAuthority('MEDIA_UPLOAD')")
    String embed(@AuthenticationPrincipal SchoolUser me, @RequestParam String url, @RequestParam(required = false) String title,
                 RedirectAttributes redirect) {
        try {
            library.embedVideo(url, title, me.id());
            redirect.addFlashAttribute("notice", "Video agregado; queda pendiente de revisión");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/media";
    }

    @PostMapping("/folders")
    @PreAuthorize("hasAuthority('MEDIA_UPLOAD')")
    String createFolder(@RequestParam String name, RedirectAttributes redirect) {
        try {
            library.createFolder(name);
            redirect.addFlashAttribute("notice", "Carpeta creada");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/media";
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        MediaAsset asset = library.get(id);
        model.addAttribute("asset", asset);
        model.addAttribute("folders", library.folders());
        model.addAttribute("tags", asset.getTags().stream().map(t -> t.getName()).sorted().collect(Collectors.joining(", ")));
        model.addAttribute("regions", BlurRegions.format(asset.getBlurRegions()));
        return "admin/media/edit";
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @RequestParam(required = false) String altText,
                  @RequestParam(required = false) String caption, @RequestParam(required = false) String credits,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate takenOn,
                  @RequestParam(required = false) Long folderId, @RequestParam(required = false) String tags,
                  RedirectAttributes redirect) {
        return run(redirect, id, "Datos guardados",
                () -> library.update(id, new MediaDetails(altText, caption, credits, takenOn, folderId, tagSet(tags))));
    }

    @PostMapping("/{id}/blur")
    String blur(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) String regions,
                RedirectAttributes redirect) {
        return run(redirect, id, "Difuminado aplicado: el sitio muestra solo la versión difuminada",
                () -> library.blur(id, BlurRegions.parse(regions), me.id()));
    }

    @PostMapping("/{id}/withdraw")
    String withdraw(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam(required = false) String reason,
                    RedirectAttributes redirect) {
        return run(redirect, id, "Retirada de todo el sitio", () -> library.withdraw(id, reason, me.id()));
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('MEDIA_UPLOAD')")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            library.delete(id);
            redirect.addFlashAttribute("notice", "Eliminado de la biblioteca");
            return "redirect:/admin/media";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/media/" + id;
        }
    }

    static List<MediaLibrary.Upload> read(List<MultipartFile> files) {
        List<MediaLibrary.Upload> uploads = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            try {
                uploads.add(new MediaLibrary.Upload(file.getOriginalFilename(), file.getBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (uploads.isEmpty()) {
            throw new RuleViolation("Elige al menos una foto");
        }
        return uploads;
    }

    static Set<String> tagSet(String tags) {
        if (tags == null || tags.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(tags.split(",")).map(String::strip).filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation | FileUploadException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/media/" + id;
    }
}
