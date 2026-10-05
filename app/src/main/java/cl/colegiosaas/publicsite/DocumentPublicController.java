package cl.colegiosaas.publicsite;

import cl.colegiosaas.documents.DocumentService;
import cl.colegiosaas.documents.InstitutionalDocument;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Documentos institucionales públicos (DOC-01): por categoría, con los protocolos y anexos bajo su
 * Reglamento Interno, los datos que exige la REX 781 (DOC-02) y el historial de versiones (DOC-03).
 */
@Controller
class DocumentPublicController {

    private final DocumentService documents;
    private final PublicPages pages;

    DocumentPublicController(DocumentService documents, PublicPages pages) {
        this.documents = documents;
        this.pages = pages;
    }

    @GetMapping("/documentos")
    String list(Model model) {
        List<InstitutionalDocument> published = documents.published();
        // Los protocolos y anexos se muestran dentro de su Reglamento Interno, no sueltos.
        Map<Long, List<InstitutionalDocument>> children = published.stream()
                .filter(d -> d.getParent() != null)
                .collect(Collectors.groupingBy(d -> d.getParent().getId()));
        Map<String, List<InstitutionalDocument>> byCategory = published.stream()
                .filter(d -> d.getParent() == null || published.stream().noneMatch(p -> p.getId().equals(d.getParent().getId())))
                .collect(Collectors.groupingBy(d -> d.getCategory().name(), LinkedHashMap::new, Collectors.toList()));
        pages.prepare(model, "Documentos institucionales", "Reglamento Interno, protocolos, planes y documentos oficiales del colegio");
        model.addAttribute("groups", byCategory);
        model.addAttribute("children", children);
        return "public/documents/list";
    }

    @GetMapping("/documentos/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}")
    String document(@PathVariable String slug, Model model) {
        InstitutionalDocument document = documents.publishedBySlug(slug);
        pages.prepare(model, document.getTitle(), document.getDescription());
        model.addAttribute("document", document);
        return "public/documents/document";
    }
}
