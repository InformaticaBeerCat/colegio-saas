package cl.colegiosaas.page.web;

import cl.colegiosaas.documents.DocumentCategory;
import cl.colegiosaas.info.FaqCategoryRepository;
import cl.colegiosaas.media.AlbumService;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.BlockType;
import cl.colegiosaas.page.NotFoundException;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageException;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.publicsite.SiteContextService;
import cl.colegiosaas.site.SiteSection;
import jakarta.validation.Valid;
import org.jsoup.Jsoup;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Constructor de páginas (CFG-04): datos de la página, sus bloques en borrador y la publicación. */
@Controller
@RequestMapping("/admin/pages")
@PreAuthorize("hasAuthority('PAGES')")
class PageAdminController {

    private final PageService pages;
    private final FaqCategoryRepository faqCategories;
    private final MediaLibrary media;
    private final AlbumService albums;

    PageAdminController(PageService pages, FaqCategoryRepository faqCategories, MediaLibrary media, AlbumService albums) {
        this.pages = pages;
        this.faqCategories = faqCategories;
        this.media = media;
        this.albums = albums;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("pages", pages.list());
        model.addAttribute("form", new NewPageForm());
        model.addAttribute("kinds", pages.creatableKinds());
        return "admin/pages/list";
    }

    @PostMapping
    String create(@ModelAttribute("form") NewPageForm form, Model model, RedirectAttributes redirect) {
        try {
            Page page = pages.create(form.getTitle(), form.getSlug(), form.getKind());
            redirect.addFlashAttribute("notice", "Página creada. Agrega bloques y publícala cuando esté lista.");
            return "redirect:/admin/pages/" + page.getId();
        } catch (PageException e) {
            model.addAttribute("problem", e.getMessage());
            model.addAttribute("pages", pages.list());
            model.addAttribute("kinds", pages.creatableKinds());
            return "admin/pages/list";
        }
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        Page page = pages.get(id);
        return showEditor(page, PageDetailsForm.of(page), model);
    }

    @PostMapping("/{id}")
    String updateDetails(@PathVariable long id, @Valid @ModelAttribute("details") PageDetailsForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                pages.updateDetails(id, form.toDetails());
                redirect.addFlashAttribute("notice", "Datos de la página guardados");
                return "redirect:/admin/pages/" + id;
            } catch (PageException e) {
                errors.reject("page", e.getMessage());
            }
        }
        return showEditor(pages.get(id), form, model);
    }

    @PostMapping("/{id}/publish")
    String publish(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Página publicada", () -> pages.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    String unpublish(@PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Página retirada del sitio; su contenido se conserva", () -> pages.unpublish(id));
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            pages.delete(id);
            redirect.addFlashAttribute("notice", "Página eliminada");
            return "redirect:/admin/pages";
        } catch (PageException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/pages/" + id;
        }
    }

    // --- Bloques ---

    @GetMapping("/{id}/blocks/new")
    String newBlock(@PathVariable long id, @RequestParam String type, Model model) {
        BlockType blockType = BlockType.byKey(type).filter(BlockType::available)
                .orElseThrow(() -> new NotFoundException("Tipo de bloque no disponible"));
        return showBlockForm(pages.get(id), BlockForm.empty(blockType), null, model);
    }

    @PostMapping("/{id}/blocks")
    String addBlock(@PathVariable long id, @ModelAttribute("block") BlockForm form, Model model, RedirectAttributes redirect) {
        try {
            if (!form.blockType().available()) {
                throw new PageException("Tipo de bloque no disponible");
            }
            pages.addBlock(id, form.toBlock());
            redirect.addFlashAttribute("notice", "Bloque agregado al borrador");
            return "redirect:/admin/pages/" + id + "#bloques";
        } catch (PageException e) {
            model.addAttribute("problem", e.getMessage());
            return showBlockForm(pages.get(id), form, null, model);
        }
    }

    @GetMapping("/{id}/blocks/{index}")
    String editBlock(@PathVariable long id, @PathVariable int index, Model model) {
        Page page = pages.get(id);
        List<Block> blocks = page.getDraftBlocks();
        if (index < 0 || index >= blocks.size()) {
            throw new NotFoundException("El bloque ya no existe");
        }
        return showBlockForm(page, BlockForm.of(blocks.get(index)), index, model);
    }

    @PostMapping("/{id}/blocks/{index}")
    String replaceBlock(@PathVariable long id, @PathVariable int index, @ModelAttribute("block") BlockForm form,
                        Model model, RedirectAttributes redirect) {
        try {
            pages.replaceBlock(id, index, form.toBlock());
            redirect.addFlashAttribute("notice", "Bloque guardado en el borrador");
            return "redirect:/admin/pages/" + id + "#bloques";
        } catch (PageException e) {
            model.addAttribute("problem", e.getMessage());
            return showBlockForm(pages.get(id), form, index, model);
        }
    }

    @PostMapping("/{id}/blocks/{index}/move")
    String moveBlock(@PathVariable long id, @PathVariable int index, @RequestParam int direction, RedirectAttributes redirect) {
        return run(redirect, id, null, () -> pages.moveBlock(id, index, direction));
    }

    @PostMapping("/{id}/blocks/{index}/delete")
    String removeBlock(@PathVariable long id, @PathVariable int index, RedirectAttributes redirect) {
        return run(redirect, id, "Bloque quitado del borrador", () -> pages.removeBlock(id, index));
    }

    private String showEditor(Page page, PageDetailsForm details, Model model) {
        model.addAttribute("page", page);
        model.addAttribute("details", details);
        model.addAttribute("sections", SiteSection.values());
        model.addAttribute("blocks", rows(page.getDraftBlocks()));
        model.addAttribute("blockTypes", Arrays.stream(BlockType.values()).filter(BlockType::available).toList());
        model.addAttribute("previewUrl", SiteContextService.linkTo(page, true));
        model.addAttribute("publicUrl", SiteContextService.linkTo(page, false));
        model.addAttribute("isHome", page.getKind() == PageKind.HOME);
        return "admin/pages/edit";
    }

    private String showBlockForm(Page page, BlockForm form, Integer index, Model model) {
        model.addAttribute("page", page);
        model.addAttribute("block", form);
        model.addAttribute("index", index);
        model.addAttribute("faqCategories", faqCategories.findAllByOrderBySortOrderAsc());
        model.addAttribute("documentCategories", DocumentCategory.values());
        // Solo fotos aprobadas o exentas: una foto pendiente no se puede poner en una página.
        model.addAttribute("images", "hero".equals(form.getType()) ? media.displayableImages() : List.of());
        model.addAttribute("albums", "gallery".equals(form.getType()) ? albums.list() : List.of());
        return "admin/pages/block";
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            if (success != null) {
                redirect.addFlashAttribute("notice", success);
            }
        } catch (PageException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/pages/" + id + "#bloques";
    }

    /** Resumen de cada bloque para la lista del editor: su tipo y el título o el comienzo del texto. */
    private static List<BlockRow> rows(List<Block> blocks) {
        List<BlockRow> rows = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            rows.add(new BlockRow(i, BlockType.of(block).key(), summary(block)));
        }
        return rows;
    }

    private static String summary(Block block) {
        String text = switch (block) {
            case Block.Hero b -> b.title();
            case Block.RichText b -> Jsoup.parse(b.html()).text();
            case Block.QuickLinks b -> b.title();
            case Block.LatestNews b -> b.title();
            case Block.UpcomingEvents b -> b.title();
            case Block.Stats b -> b.title();
            case Block.Testimonials b -> b.title();
            case Block.CallToAction b -> b.title();
            case Block.Gallery b -> b.title();
            case Block.Timeline b -> b.title();
            case Block.Location b -> b.title();
            case Block.Faq b -> b.title();
            case Block.Documents b -> b.title();
            case Block.ReportChannel b -> b.title();
        };
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 77) + "…" : text;
    }

    public record BlockRow(int index, String type, String summary) {
    }
}
