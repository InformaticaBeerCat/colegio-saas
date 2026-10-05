package cl.colegiosaas.site;

import cl.colegiosaas.site.SiteDesign.ColorScheme;
import cl.colegiosaas.site.SiteDesign.CornerRadius;
import cl.colegiosaas.site.SiteDesign.Palette;
import cl.colegiosaas.site.SiteDesign.Shadow;
import cl.colegiosaas.site.SiteDesign.Typography;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Los 3 temas base (CFG-02). El tema define la estructura visual (encabezado, tarjetas, portada) y
 * cada variante es un punto de partida de colores; después el colegio ajusta los tokens a su marca.
 * El id se guarda en {@link SiteDesign#theme()}: no se renombra sin migrar los datos.
 *
 * Todas las variantes pasan la verificación AA de {@link DesignReview} (lo comprueba un test).
 */
public enum Theme {

    /** Institucional: serif en títulos, barra de navegación en el color del colegio. Para colegios tradicionales. */
    CLASSIC("classic", new Typography("Merriweather", "Source Sans 3"), CornerRadius.SMALL, Shadow.SOFT,
            new Variant("navy", palette("#1F3A5F", "#2E5E4E", "#C9962B", "#FFFFFF", "#F5F3EE", "#1C1C1C")),
            new Variant("burgundy", palette("#7A1F2B", "#3D3D3D", "#B8862B", "#FFFFFF", "#F7F2EF", "#1C1C1C")),
            new Variant("forest", palette("#1E5631", "#2F3E46", "#D4A017", "#FFFFFF", "#F2F5F1", "#1A1A1A"))),

    /** Moderno: sans serif geométrica, encabezado blanco y portada amplia. */
    MODERN("modern", new Typography("Montserrat", "Inter"), CornerRadius.MEDIUM, Shadow.STRONG,
            new Variant("ocean", palette("#0B5CAD", "#0F766E", "#F59E0B", "#FFFFFF", "#F1F5F9", "#0F172A")),
            new Variant("violet", palette("#5B21B6", "#BE185D", "#F97316", "#FFFFFF", "#F5F3FF", "#1E1B4B")),
            new Variant("graphite", palette("#1F2937", "#2563EB", "#10B981", "#FFFFFF", "#F3F4F6", "#111827"))),

    /** Cercano: formas redondeadas y tipografía amable. Pensado para básica y párvulos. */
    FRIENDLY("friendly", new Typography("Nunito", "Nunito"), CornerRadius.LARGE, Shadow.SOFT,
            new Variant("sun", palette("#B45309", "#0E7490", "#FCD34D", "#FFFDF7", "#FFF4DB", "#292524")),
            new Variant("sea", palette("#0F766E", "#1D4ED8", "#FB923C", "#FFFFFF", "#ECFDF5", "#1F2937")),
            new Variant("sky", palette("#1D4ED8", "#7C3AED", "#FACC15", "#FFFFFF", "#EFF6FF", "#1E293B")));

    private final String id;
    private final Typography typography;
    private final CornerRadius cornerRadius;
    private final Shadow shadow;
    private final List<Variant> variants;

    Theme(String id, Typography typography, CornerRadius cornerRadius, Shadow shadow, Variant... variants) {
        this.id = id;
        this.typography = typography;
        this.cornerRadius = cornerRadius;
        this.shadow = shadow;
        this.variants = List.of(variants);
    }

    public String id() {
        return id;
    }

    public List<Variant> variants() {
        return variants;
    }

    public Variant defaultVariant() {
        return variants.getFirst();
    }

    public Optional<Variant> variant(String variantId) {
        return variants.stream().filter(v -> v.id().equals(variantId)).findFirst();
    }

    /** Diseño completo del tema con esa variante: es lo que aplica el asistente o el botón "usar este tema". */
    public SiteDesign design(String variantId) {
        Variant variant = variant(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variante desconocida para " + id + ": " + variantId));
        return new SiteDesign(id, variant.id(), variant.palette(), typography, cornerRadius, shadow, ColorScheme.LIGHT);
    }

    public static Optional<Theme> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }

    /** Un diseño guardado con un tema que ya no existe se muestra con la estructura del tema por defecto. */
    public static Theme of(SiteDesign design) {
        return byId(design.theme()).orElse(CLASSIC);
    }

    public record Variant(String id, Palette palette) {
    }

    private static Palette palette(String primary, String secondary, String accent,
                                   String background, String surface, String text) {
        return new Palette(HexColor.of(primary), HexColor.of(secondary), HexColor.of(accent),
                HexColor.of(background), HexColor.of(surface), HexColor.of(text));
    }
}
