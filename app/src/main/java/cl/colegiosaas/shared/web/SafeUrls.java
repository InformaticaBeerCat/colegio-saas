package cl.colegiosaas.shared.web;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Enlaces que escriben los editores (menú, botones, redes sociales). Thymeleaf escapa el texto pero no
 * revisa el esquema: un {@code javascript:} en un href se ejecutaría. Solo se aceptan rutas del sitio y
 * esquemas conocidos.
 */
public final class SafeUrls {

    private static final Pattern ALLOWED = Pattern.compile(
            "(/(?!/)\\S*|#[\\w-]*|https?://[^\\s/]+\\S*|mailto:\\S+@\\S+|tel:\\+?[\\d\\s-]+)");

    private SafeUrls() {
    }

    public static boolean isAllowed(String url) {
        return url != null && ALLOWED.matcher(url.strip()).matches();
    }

    public static boolean isExternal(String url) {
        return url != null && url.strip().toLowerCase(Locale.ROOT).startsWith("http");
    }

    public static String require(String url) {
        if (!isAllowed(url)) {
            throw new IllegalArgumentException("Enlace no permitido: usa una ruta del sitio (/admision) o una dirección https://");
        }
        return url.strip();
    }
}
