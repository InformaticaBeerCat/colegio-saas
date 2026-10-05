package cl.colegiosaas.media.web;

import cl.colegiosaas.media.Album;
import cl.colegiosaas.media.AlbumService;
import cl.colegiosaas.media.AlbumVisibility;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaKind;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.StructureService;
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
import java.util.List;
import java.util.Set;

/** Álbumes en el panel (MED-05, MED-06). */
@Controller
@RequestMapping("/admin/albums")
@RequiresFeature(Feature.GALLERIES)
@PreAuthorize("hasAuthority('MEDIA_UPLOAD')")
class AlbumAdminController {

    private final AlbumService albums;
    private final MediaLibrary library;
    private final StructureService structure;
    private final SchoolTime time;

    AlbumAdminController(AlbumService albums, MediaLibrary library, StructureService structure, SchoolTime time) {
        this.albums = albums;
        this.library = library;
        this.structure = structure;
        this.time = time;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("albums", albums.list());
        return "admin/albums/list";
    }

    @PostMapping
    String create(@RequestParam String title, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate takenOn,
                  RedirectAttributes redirect) {
        try {
            Album album = albums.create(title, takenOn);
            return "redirect:/admin/albums/" + album.getId();
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/albums";
        }
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        Album album = albums.get(id);
        List<MediaAsset> available = library.list(null, null, null).stream()
                .filter(a -> a.getKind() != MediaKind.DOCUMENT && !a.isWithdrawn() && !album.contains(a))
                .limit(120)
                .toList();
        model.addAttribute("album", album);
        model.addAttribute("available", available);
        model.addAttribute("visibilities", AlbumVisibility.values());
        model.addAttribute("courses", structure.courses(time.today().getYear()));
        return "admin/albums/edit";
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @RequestParam String title, @RequestParam(required = false) String description,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate takenOn,
                  @RequestParam AlbumVisibility visibility, @RequestParam(required = false) Long courseId,
                  RedirectAttributes redirect) {
        return run(redirect, id, "Álbum guardado", () -> albums.update(id, title, description, takenOn, visibility, courseId));
    }

    @PostMapping("/{id}/assets")
    String add(@PathVariable long id, @RequestParam(name = "assetIds", required = false) Set<Long> assetIds, RedirectAttributes redirect) {
        int added = assetIds == null ? 0 : albums.addAssets(id, assetIds);
        redirect.addFlashAttribute("notice", added + " elemento(s) agregado(s)");
        return "redirect:/admin/albums/" + id;
    }

    /** Sube fotos directo al álbum: pasan a la biblioteca y quedan pendientes de revisión. */
    @PostMapping("/{id}/upload")
    String upload(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, @RequestParam("files") List<MultipartFile> files,
                  RedirectAttributes redirect) {
        try {
            String tag = albums.get(id).getSlug();
            List<MediaLibrary.UploadResult> results = library.uploadImages(MediaAdminController.read(files), me.id(), null, Set.of(tag));
            albums.addAssets(id, results.stream().filter(MediaLibrary.UploadResult::ok).map(MediaLibrary.UploadResult::assetId).toList());
            long ok = results.stream().filter(MediaLibrary.UploadResult::ok).count();
            redirect.addFlashAttribute("notice", ok + " foto(s) agregada(s); se verán cuando el gestor de consentimientos las apruebe");
            List<String> failed = results.stream().filter(r -> !r.ok()).map(r -> r.name() + ": " + r.problem()).toList();
            if (!failed.isEmpty()) {
                redirect.addFlashAttribute("problem", String.join(" · ", failed));
            }
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/albums/" + id;
    }

    @PostMapping("/{id}/assets/{assetId}/remove")
    String remove(@PathVariable long id, @PathVariable long assetId) {
        albums.removeAsset(id, assetId);
        return "redirect:/admin/albums/" + id;
    }

    @PostMapping("/{id}/assets/{assetId}/move")
    String move(@PathVariable long id, @PathVariable long assetId, @RequestParam int direction) {
        albums.move(id, assetId, direction);
        return "redirect:/admin/albums/" + id;
    }

    @PostMapping("/{id}/assets/{assetId}/cover")
    String cover(@PathVariable long id, @PathVariable long assetId, RedirectAttributes redirect) {
        return run(redirect, id, "Portada elegida", () -> albums.setCover(id, assetId));
    }

    @PostMapping("/{id}/publish")
    String publish(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Álbum publicado", () -> albums.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    String unpublish(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Álbum retirado del sitio", () -> albums.unpublish(id));
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            albums.delete(id);
            redirect.addFlashAttribute("notice", "Álbum eliminado (las fotos siguen en la biblioteca)");
            return "redirect:/admin/albums";
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/albums/" + id;
        }
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/albums/" + id;
    }
}
