package cl.colegiosaas.site;

import cl.colegiosaas.site.SiteDesign.Palette;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Convierte un {@link SiteDesign} en la hoja de estilos con los tokens del sitio (variables CSS).
 * La estructura de cada tema vive en {@code static/css/site.css}; aquí solo van colores, fuentes,
 * radios y sombras. Se sirve como archivo (no como {@code <style>} en la página) porque la CSP no
 * permite estilos en línea.
 *
 * @param css     contenido de la hoja
 * @param version huella del contenido, para la URL ({@code theme.css?v=…}) y el caché del navegador
 */
public record ThemeStylesheet(String css, String version) {

    /** Fondo, superficie y texto de la versión oscura automática (esquema AUTO). */
    private static final HexColor DARK_BACKGROUND = HexColor.of("#121417");
    private static final HexColor DARK_SURFACE = HexColor.of("#1C2026");
    private static final HexColor DARK_TEXT = HexColor.of("#ECEEF1");

    /**
     * Ruta de las fuentes relativa a la hoja: tanto {@code /site/theme.css} como la de vista previa
     * ({@code /admin/preview-theme.css}) están a un nivel de la raíz, y así funciona con cualquier context path.
     */
    private static final String FONT_BASE_URL = "../fonts/";

    public static ThemeStylesheet of(SiteDesign design) {
        StringBuilder css = new StringBuilder("/* Tokens del sitio: se generan desde el diseño publicado. */\n");
        fontFaces(design, FONT_BASE_URL, css);

        css.append(":root {\n");
        css.append("  color-scheme: ").append(design.colorScheme() == SiteDesign.ColorScheme.DARK ? "dark" : "light")
                .append(";\n");
        colors(design.palette(), css);
        css.append("  --font-heading: ").append(fontStack(design.typography().headingFont())).append(";\n");
        css.append("  --font-body: ").append(fontStack(design.typography().bodyFont())).append(";\n");
        css.append("  --radius: ").append(radius(design.cornerRadius())).append(";\n");
        css.append("  --radius-lg: ").append(largeRadius(design.cornerRadius())).append(";\n");
        css.append("  --shadow: ").append(shadow(design.shadow())).append(";\n");
        css.append("}\n");

        if (design.colorScheme() == SiteDesign.ColorScheme.AUTO) {
            css.append("@media (prefers-color-scheme: dark) {\n:root {\n  color-scheme: dark;\n");
            colors(darkVersion(design.palette()), css);
            css.append("}\n}\n");
        }
        String content = css.toString();
        return new ThemeStylesheet(content, fingerprint(content));
    }

    /**
     * Versión oscura que conserva el tono de la marca: el primario se aclara lo justo para seguir
     * cumpliendo AA sobre el fondo oscuro.
     */
    static Palette darkVersion(Palette light) {
        return new Palette(
                Contrast.adjustToContrast(light.primary(), DARK_SURFACE, Contrast.AA_TEXT),
                light.secondary().mix(HexColor.BLACK, 0.35),
                light.accent(),
                DARK_BACKGROUND,
                DARK_SURFACE,
                DARK_TEXT);
    }

    private static void colors(Palette p, StringBuilder css) {
        HexColor onPrimary = Contrast.readableOn(p.primary());
        // Texto secundario (fechas, ayudas): más suave que el texto, pero siempre AA sobre la superficie.
        HexColor muted = Contrast.adjustToContrast(p.text().mix(p.surface(), 0.35), p.surface(), Contrast.AA_TEXT);
        // El anillo de foco necesita 3:1 contra el fondo (WCAG 1.4.11); si el acento no llega, se usa el texto.
        HexColor focus = Contrast.ratio(p.accent(), p.background()) >= Contrast.AA_UI ? p.accent() : p.text();
        HexColor border = Contrast.adjustToContrast(p.text().mix(p.background(), 0.8), p.background(), 1.5);
        // Bordes de campos de formulario: componentes de interfaz, 3:1.
        HexColor control = Contrast.adjustToContrast(p.text().mix(p.background(), 0.55), p.background(), Contrast.AA_UI);

        token(css, "primary", p.primary());
        token(css, "primary-hover", p.primary().mix(onPrimary.equals(HexColor.WHITE) ? HexColor.BLACK : HexColor.WHITE, 0.15));
        token(css, "on-primary", onPrimary);
        token(css, "secondary", p.secondary());
        token(css, "on-secondary", Contrast.readableOn(p.secondary()));
        token(css, "accent", p.accent());
        token(css, "on-accent", Contrast.readableOn(p.accent()));
        token(css, "bg", p.background());
        token(css, "surface", p.surface());
        token(css, "text", p.text());
        token(css, "muted", muted);
        token(css, "border", border);
        token(css, "control", control);
        token(css, "focus", focus);
    }

    private static void token(StringBuilder css, String name, HexColor color) {
        css.append("  --color-").append(name).append(": ").append(color.value()).append(";\n");
    }

    private static void fontFaces(SiteDesign design, String fontBaseUrl, StringBuilder css) {
        Set<FontCatalog> fonts = new LinkedHashSet<>();
        FontCatalog.byFamily(design.typography().headingFont()).ifPresent(fonts::add);
        FontCatalog.byFamily(design.typography().bodyFont()).ifPresent(fonts::add);
        for (FontCatalog font : fonts) {
            for (FontCatalog.FontFile file : font.files()) {
                css.append("@font-face {\n")
                        .append("  font-family: \"").append(font.family()).append("\";\n")
                        .append("  src: url(\"").append(fontBaseUrl).append(file.fileName()).append("\") format(\"woff2\");\n")
                        .append("  font-weight: ").append(file.weight()).append(";\n")
                        .append("  font-style: normal;\n")
                        .append("  font-display: swap;\n")
                        .append("}\n");
            }
        }
    }

    /** Una fuente fuera del catálogo (dato antiguo) cae a la del sistema; nunca se escribe tal cual en el CSS. */
    private static String fontStack(String family) {
        return FontCatalog.byFamily(family).map(FontCatalog::cssStack)
                .orElse("system-ui, -apple-system, \"Segoe UI\", Roboto, Arial, sans-serif");
    }

    private static String radius(SiteDesign.CornerRadius radius) {
        return switch (radius) {
            case NONE -> "0";
            case SMALL -> "4px";
            case MEDIUM -> "10px";
            case LARGE -> "18px";
        };
    }

    private static String largeRadius(SiteDesign.CornerRadius radius) {
        return switch (radius) {
            case NONE -> "0";
            case SMALL -> "6px";
            case MEDIUM -> "16px";
            case LARGE -> "28px";
        };
    }

    private static String shadow(SiteDesign.Shadow shadow) {
        return switch (shadow) {
            case NONE -> "none";
            case SOFT -> "0 1px 3px rgba(0, 0, 0, .08), 0 4px 12px rgba(0, 0, 0, .06)";
            case STRONG -> "0 4px 10px rgba(0, 0, 0, .10), 0 14px 32px rgba(0, 0, 0, .14)";
        };
    }

    private static String fingerprint(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
