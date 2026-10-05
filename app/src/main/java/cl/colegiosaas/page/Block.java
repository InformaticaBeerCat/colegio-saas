package cl.colegiosaas.page;

import cl.colegiosaas.documents.DocumentCategory;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Bloque del constructor de páginas (PUB-01, CFG-04). Interfaz sellada: el compilador conoce
 * todos los tipos posibles, así un {@code switch} sobre bloques avisa si falta alguno al renderizar.
 *
 * En JSON cada bloque lleva su tipo: {@code {"type":"hero","title":"…"}}. El nombre del tipo
 * queda guardado en la base: no se renombra sin migrar los datos.
 *
 * Las referencias a medios y álbumes son ids sin llave foránea; quien renderiza ignora los que
 * ya no existan o estén retirados (MED-09).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Block.Hero.class, name = "hero"),
        @JsonSubTypes.Type(value = Block.RichText.class, name = "rich-text"),
        @JsonSubTypes.Type(value = Block.QuickLinks.class, name = "quick-links"),
        @JsonSubTypes.Type(value = Block.LatestNews.class, name = "latest-news"),
        @JsonSubTypes.Type(value = Block.UpcomingEvents.class, name = "upcoming-events"),
        @JsonSubTypes.Type(value = Block.Stats.class, name = "stats"),
        @JsonSubTypes.Type(value = Block.Testimonials.class, name = "testimonials"),
        @JsonSubTypes.Type(value = Block.CallToAction.class, name = "call-to-action"),
        @JsonSubTypes.Type(value = Block.Gallery.class, name = "gallery"),
        @JsonSubTypes.Type(value = Block.Timeline.class, name = "timeline"),
        @JsonSubTypes.Type(value = Block.Location.class, name = "location"),
        @JsonSubTypes.Type(value = Block.Faq.class, name = "faq"),
        @JsonSubTypes.Type(value = Block.Documents.class, name = "documents"),
        @JsonSubTypes.Type(value = Block.ReportChannel.class, name = "report-channel"),
})
public sealed interface Block {

    /** Portada con imagen o video (UX-03: el video necesita póster liviano). */
    record Hero(String title, String subtitle, Long imageAssetId, Long videoAssetId,
                String buttonLabel, String buttonUrl) implements Block {
    }

    /** HTML libre; se sanea al guardar (fase 4). */
    record RichText(String html) implements Block {
    }

    /** Muestra los {@code QuickLink} activos. */
    record QuickLinks(String title) implements Block {
    }

    record LatestNews(String title, int count) implements Block {
    }

    record UpcomingEvents(String title, int count) implements Block {
    }

    /** Cifras: "1.200 estudiantes", "45 años"… */
    record Stats(String title, List<Stat> items) implements Block {
        public Stats {
            items = List.copyOf(items);
        }
    }

    record Stat(String value, String label) {
    }

    record Testimonials(String title, List<Testimonial> items) implements Block {
        public Testimonials {
            items = List.copyOf(items);
        }
    }

    record Testimonial(String quote, String author, String role) {
    }

    /** Llamado a la acción, típicamente admisión. */
    record CallToAction(String title, String text, String buttonLabel, String buttonUrl) implements Block {
    }

    record Gallery(String title, Long albumId) implements Block {
    }

    /** Historia del colegio por hitos. */
    record Timeline(String title, List<TimelineEntry> entries) implements Block {
        public Timeline {
            entries = List.copyOf(entries);
        }
    }

    record TimelineEntry(String year, String title, String text) {
    }

    /** Mapa con la dirección del perfil del colegio. */
    record Location(String title) implements Block {
    }

    /** Preguntas frecuentes; sin categoría muestra todas. */
    record Faq(String title, Long categoryId) implements Block {
    }

    /** Documentos institucionales vigentes de esas categorías (p. ej. protocolos en la página de convivencia). */
    record Documents(String title, List<DocumentCategory> categories) implements Block {
        public Documents {
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    /**
     * Canal de denuncia de convivencia escolar (DOC-07): a quién acudir y cómo. El formulario propio llega
     * con el módulo de contacto (fase 7); mientras, {@code formUrl} puede apuntar a uno externo.
     */
    record ReportChannel(String title, String text, String email, String phone, String inPerson, String formUrl)
            implements Block {
    }
}
