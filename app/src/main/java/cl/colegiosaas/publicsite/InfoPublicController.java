package cl.colegiosaas.publicsite;

import cl.colegiosaas.info.FaqEntry;
import cl.colegiosaas.info.GeneralInfoService;
import cl.colegiosaas.info.InfoSheet;
import cl.colegiosaas.info.InfoSheetKind;
import cl.colegiosaas.platform.SchoolTime;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Preguntas frecuentes (PUB-09), talleres (PUB-06) y útiles, uniforme y minuta (PUB-10). */
@Controller
class InfoPublicController {

    private final GeneralInfoService info;
    private final SchoolTime time;
    private final PublicPages pages;
    private final StructuredData structuredData;

    InfoPublicController(GeneralInfoService info, SchoolTime time, PublicPages pages, StructuredData structuredData) {
        this.info = info;
        this.time = time;
        this.pages = pages;
        this.structuredData = structuredData;
    }

    @GetMapping("/preguntas-frecuentes")
    String faq(Model model) {
        Map<String, List<FaqEntry>> byCategory = info.publishedFaq().stream()
                .collect(Collectors.groupingBy(e -> e.getCategory().getName(), LinkedHashMap::new, Collectors.toList()));
        pages.prepare(model, "Preguntas frecuentes", "Respuestas a las dudas más comunes de las familias");
        model.addAttribute("faq", byCategory);
        List<FaqEntry> all = byCategory.values().stream().flatMap(List::stream).toList();
        if (!all.isEmpty()) {
            model.addAttribute("seo", pages.seo(model).withJsonLd(structuredData.faq(all)));
        }
        return "public/info/faq";
    }

    @GetMapping("/talleres")
    String workshops(Model model) {
        pages.prepare(model, "Talleres", "Talleres y actividades extraprogramáticas");
        model.addAttribute("workshops", info.activeWorkshops(time.today().getYear()));
        model.addAttribute("year", time.today().getYear());
        return "public/info/workshops";
    }

    @GetMapping("/informacion-practica")
    String sheets(Model model) {
        Map<InfoSheetKind, List<InfoSheet>> byKind = info.currentSheets(time.today()).stream()
                .collect(Collectors.groupingBy(InfoSheet::getKind, LinkedHashMap::new, Collectors.toList()));
        pages.prepare(model, "Útiles, uniforme y minuta", "Listas de útiles, uniforme escolar y minuta de alimentación");
        model.addAttribute("sheets", byKind);
        return "public/info/sheets";
    }
}
