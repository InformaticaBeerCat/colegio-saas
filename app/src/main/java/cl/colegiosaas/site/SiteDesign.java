package cl.colegiosaas.site;

import java.util.Objects;

/**
 * Diseño del sitio: tema base + tokens editables (CFG-02, CFG-03). Es inmutable: cada cambio
 * produce un diseño nuevo, lo que hace trivial comparar borrador con publicado.
 * Se guarda como una sola columna JSON.
 */
public record SiteDesign(
        String theme,
        String variant,
        Palette palette,
        Typography typography,
        CornerRadius cornerRadius,
        Shadow shadow,
        ColorScheme colorScheme) {

    public SiteDesign {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(typography, "typography");
    }

    /** Punto de partida neutro; los 3 temas base reales se definen en la fase 3. */
    public static SiteDesign defaults() {
        return new SiteDesign(
                "base",
                "default",
                new Palette(
                        HexColor.of("#1F3A5F"),
                        HexColor.of("#2E7D5B"),
                        HexColor.of("#E0A526"),
                        HexColor.of("#FFFFFF"),
                        HexColor.of("#F4F5F7"),
                        HexColor.of("#1A1A1A")),
                new Typography("Merriweather", "Inter"),
                CornerRadius.MEDIUM,
                Shadow.SOFT,
                ColorScheme.LIGHT);
    }

    public SiteDesign withPalette(Palette newPalette) {
        return new SiteDesign(theme, variant, newPalette, typography, cornerRadius, shadow, colorScheme);
    }

    public SiteDesign withTheme(String newTheme, String newVariant) {
        return new SiteDesign(newTheme, newVariant, palette, typography, cornerRadius, shadow, colorScheme);
    }

    /** El contraste AA entre texto y fondo se verifica al editar (fase 3), no aquí. */
    public record Palette(
            HexColor primary,
            HexColor secondary,
            HexColor accent,
            HexColor background,
            HexColor surface,
            HexColor text) {
    }

    /** Solo fuentes del catálogo con licencia libre (Ley 17.336); el catálogo llega en la fase 3. */
    public record Typography(String headingFont, String bodyFont) {
    }

    public enum CornerRadius { NONE, SMALL, MEDIUM, LARGE }

    public enum Shadow { NONE, SOFT, STRONG }

    public enum ColorScheme { LIGHT, DARK, AUTO }
}
