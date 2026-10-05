package cl.colegiosaas.publicsite;

import cl.colegiosaas.news.AnnouncementService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Comunicados y circulares públicos (NOT-03). Comunicados no depende de un módulo: es parte del plan base. */
@Controller
class AnnouncementPublicController {

    private final AnnouncementService announcements;
    private final PublicPages pages;

    AnnouncementPublicController(AnnouncementService announcements, PublicPages pages) {
        this.announcements = announcements;
        this.pages = pages;
    }

    @GetMapping("/comunicados")
    String list(@RequestParam(name = "pagina", defaultValue = "1") int pageNumber, Model model) {
        pages.prepare(model, "Comunicados", "Comunicados y circulares del colegio");
        model.addAttribute("announcements", announcements.published(PageRequest.of(Math.max(0, pageNumber - 1), 15)));
        return "public/announcements";
    }
}
