package cl.colegiosaas.shared.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Partes de URL legibles: "Reunión de Apoderados 1°A" → "reunion-de-apoderados-1-a". */
public final class Slugs {

    public static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_LENGTH = 100;

    private Slugs() {
    }

    public static String slugify(String text) {
        if (text == null) {
            return "";
        }
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-$", "");
        }
        return slug;
    }

    /** Agrega "-2", "-3"… hasta encontrar uno libre. Vacío (título sin letras) parte de {@code fallback}. */
    public static String unique(String text, String fallback, Predicate<String> taken) {
        String base = slugify(text);
        if (base.isEmpty()) {
            base = fallback;
        }
        String candidate = base;
        for (int n = 2; taken.test(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    public static boolean isValid(String slug) {
        return slug != null && FORMAT.matcher(slug).matches();
    }
}
