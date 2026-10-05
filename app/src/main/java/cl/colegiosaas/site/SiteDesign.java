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
        cornerRadius = Objects.requireNonNullElse(cornerRadius, CornerRadius.MEDIUM);
        shadow = Objects.requireNonNullElse(shadow, Shadow.SOFT);
        colorScheme = Objects.requireNonNullElse(colorScheme, ColorScheme.LIGHT);
    }

    /** Diseño con que parte una instalación: el tema institucional en azul marino. */
    public static SiteDesign defaults() {
        return Theme.CLASSIC.design(Theme.CLASSIC.defaultVariant().id());
    }

    public SiteDesign withPalette(Palette newPalette) {
        return new SiteDesign(theme, variant, newPalette, typography, cornerRadius, shadow, colorScheme);
    }

    public SiteDesign withTheme(String newTheme, String newVariant) {
        return new SiteDesign(newTheme, newVariant, palette, typography, cornerRadius, shadow, colorScheme);
    }

    /**
     * El contraste AA se verifica al editar ({@link DesignReview}), no aquí: un diseño guardado antes de
     * endurecer una regla tiene que poder leerse igual.
     */
    public record Palette(
            HexColor primary,
            HexColor secondary,
            HexColor accent,
            HexColor background,
            HexColor surface,
            HexColor text) {
    }

    /** Solo fuentes de {@link FontCatalog}, todas con licencia libre (Ley 17.336). */
    public record Typography(String headingFont, String bodyFont) {
    }

    public enum CornerRadius { NONE, SMALL, MEDIUM, LARGE }

    public enum Shadow { NONE, SOFT, STRONG }

    public enum ColorScheme { LIGHT, DARK, AUTO }
}
