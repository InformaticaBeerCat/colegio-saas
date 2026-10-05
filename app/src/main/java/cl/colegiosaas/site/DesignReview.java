package cl.colegiosaas.site;

import cl.colegiosaas.site.SiteDesign.Palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Revisión de un diseño antes de guardarlo (CFG-03, ACC-01): contraste AA de cada par de colores que
 * el tema realmente combina, y fuentes del catálogo. El editor muestra todas las verificaciones, no solo
 * las que fallan, para que se entienda qué color se usa dónde.
 *
 * @param checks   pares de colores revisados
 * @param problems problemas que no son de contraste (tema o fuente desconocidos)
 */
public record DesignReview(List<ContrastCheck> checks, List<String> problems) {

    public DesignReview {
        checks = List.copyOf(checks);
        problems = List.copyOf(problems);
    }

    public static DesignReview of(SiteDesign design) {
        Palette p = design.palette();
        List<ContrastCheck> checks = List.of(
                text("Texto sobre el fondo", p.text(), p.background()),
                text("Texto sobre tarjetas y secciones", p.text(), p.surface()),
                text("Enlaces y títulos (primario) sobre el fondo", p.primary(), p.background()),
                text("Enlaces y títulos (primario) sobre tarjetas", p.primary(), p.surface()),
                text("Texto de botones y menú sobre el primario", Contrast.readableOn(p.primary()), p.primary()),
                text("Texto del pie de página sobre el secundario", Contrast.readableOn(p.secondary()), p.secondary()),
                text("Texto de destacados sobre el acento", Contrast.readableOn(p.accent()), p.accent()));

        List<String> problems = new ArrayList<>();
        if (Theme.byId(design.theme()).filter(t -> t.variant(design.variant()).isPresent()).isEmpty()) {
            problems.add("El tema \"" + design.theme() + "\" o su variante no existe");
        }
        checkFont(design.typography().headingFont(), "títulos", problems);
        checkFont(design.typography().bodyFont(), "texto", problems);
        return new DesignReview(checks, problems);
    }

    public boolean passes() {
        return problems.isEmpty() && checks.stream().allMatch(ContrastCheck::passes);
    }

    public List<ContrastCheck> failures() {
        return checks.stream().filter(c -> !c.passes()).toList();
    }

    /** Todo lo que impide guardar, en frases para mostrar. */
    public List<String> messages() {
        List<String> messages = new ArrayList<>(problems);
        failures().forEach(c -> messages.add("%s: contraste %s, el mínimo es %s"
                .formatted(c.label(), c.formattedRatio(), c.formattedRequired())));
        return messages;
    }

    private static ContrastCheck text(String label, HexColor foreground, HexColor background) {
        return new ContrastCheck(label, foreground, background, Contrast.ratio(foreground, background), Contrast.AA_TEXT);
    }

    private static void checkFont(String family, String usage, List<String> problems) {
        if (FontCatalog.byFamily(family).isEmpty()) {
            problems.add("La fuente de " + usage + " \"" + family + "\" no está en el catálogo de fuentes libres");
        }
    }

    public record ContrastCheck(String label, HexColor foreground, HexColor background, double ratio, double required) {

        private static final Locale CHILE = Locale.forLanguageTag("es-CL");

        public boolean passes() {
            return ratio >= required;
        }

        /** "4,52:1": coma decimal, como se escribe en Chile. */
        public String formattedRatio() {
            return format(Math.floor(ratio * 100) / 100);
        }

        public String formattedRequired() {
            return format(required);
        }

        private static String format(double value) {
            return String.format(CHILE, "%.2f:1", value);
        }
    }
}
