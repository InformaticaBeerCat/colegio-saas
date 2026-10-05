package cl.colegiosaas.shared.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Limpia el HTML que escriben los editores antes de guardarlo: deja formato, listas, tablas y enlaces,
 * y quita scripts, estilos en línea, iframes y atributos de eventos. Se guarda ya saneado, así el sitio
 * público lo puede mostrar con {@code th:utext} sin más trámite.
 *
 * Tampoco deja imágenes: toda foto pasa por la biblioteca de medios y su revisión de autorizaciones
 * (MED-06); una imagen pegada en el texto se la saltaría.
 */
public final class HtmlSanitizer {

    private static final Safelist RICH_TEXT = Safelist.relaxed()
            .removeTags("img")
            .addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .addProtocols("a", "href", "#", "tel")
            .preserveRelativeLinks(true);

    /**
     * jsoup solo acepta enlaces relativos ("/admision") si puede resolverlos contra una base; con
     * {@code preserveRelativeLinks} se guardan tal cual, así que la base no aparece en el resultado.
     */
    private static final String BASE_URI = "https://sitio.invalid";

    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

    private HtmlSanitizer() {
    }

    public static String richText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, BASE_URI, RICH_TEXT, OUTPUT);
    }
}
