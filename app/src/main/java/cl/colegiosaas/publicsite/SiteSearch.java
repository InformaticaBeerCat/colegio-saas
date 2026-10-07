package cl.colegiosaas.publicsite;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Búsqueda interna del sitio (UX-06). Un colegio publica cientos de cosas, no millones: se recorre el contenido
 * público en memoria, sin índice aparte que mantener. No distingue mayúsculas ni tildes ("matricula" encuentra
 * "Matrícula"); todas las palabras deben aparecer y pesa más encontrarlas en el título.
 */
@Component
public class SiteSearch {

    public static final int MAX_RESULTS = 30;
    private static final int SNIPPET = 180;

    private final PublicContent content;

    SiteSearch(PublicContent content) {
        this.content = content;
    }

    /** Un resultado con un extracto alrededor de la primera coincidencia. */
    public record Result(String section, String title, String path, String snippet) {
    }

    public List<Result> search(String query) {
        List<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        record Scored(PublicContent.Item item, int score) {
        }
        List<Scored> matches = new ArrayList<>();
        for (PublicContent.Item item : content.items()) {
            String title = normalize(item.title());
            String text = normalize(item.text());
            int score = 0;
            boolean all = true;
            for (String term : terms) {
                int inTitle = count(title, term);
                int inText = count(text, term);
                if (inTitle + inText == 0) {
                    all = false;
                    break;
                }
                score += inTitle * 10 + Math.min(inText, 5);
            }
            if (all) {
                matches.add(new Scored(item, score));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(s -> s.item().modified(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RESULTS)
                .map(s -> new Result(s.item().section(), s.item().title(), s.item().path(), snippet(s.item().text(), terms)))
                .toList();
    }

    /** Palabras de dos o más letras; se ignoran las demás y se limita el largo de la consulta. */
    static List<String> terms(String query) {
        if (query == null) {
            return List.of();
        }
        String clean = query.length() > 100 ? query.substring(0, 100) : query;
        return Arrays.stream(normalize(clean).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 2)
                .distinct()
                .limit(8)
                .toList();
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Extracto del texto original alrededor de la primera palabra encontrada. Quitar tildes no cambia el largo de
     * las letras en español (NFD + quitar marcas deja una letra por letra), así las posiciones coinciden.
     */
    static String snippet(String text, List<String> terms) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = normalize(text);
        int at = terms.stream().mapToInt(normalized::indexOf).filter(i -> i >= 0).min().orElse(0);
        if (normalized.length() != text.length()) {
            at = 0;
        }
        int start = Math.max(0, at - 60);
        int end = Math.min(text.length(), start + SNIPPET);
        String piece = text.substring(start, end).strip();
        return (start > 0 ? "…" : "") + piece + (end < text.length() ? "…" : "");
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
