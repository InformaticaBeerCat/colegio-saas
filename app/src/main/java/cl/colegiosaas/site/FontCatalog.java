package cl.colegiosaas.site;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Fuentes disponibles para el sitio (CFG-03). Todas tienen licencia libre (SIL OFL 1.1 o Apache 2.0,
 * textos en {@code static/fonts/licenses}) y se sirven desde el propio sitio: no se llama a Google Fonts,
 * así el navegador de los visitantes no le entrega su IP a un tercero (Ley 21.719) y la CSP queda en 'self'.
 *
 * Solo el subconjunto latino (incluye tildes, ñ, ¿ y ¡). Las variables cubren todos los pesos con un archivo.
 */
public enum FontCatalog {
    INTER("Inter", Category.SANS, "OFL-1.1", new FontFile("inter.woff2", "100 900")),
    SOURCE_SANS_3("Source Sans 3", Category.SANS, "OFL-1.1", new FontFile("source-sans-3.woff2", "200 900")),
    OPEN_SANS("Open Sans", Category.SANS, "OFL-1.1", new FontFile("open-sans.woff2", "300 800")),
    MONTSERRAT("Montserrat", Category.SANS, "OFL-1.1", new FontFile("montserrat.woff2", "100 900")),
    NUNITO("Nunito", Category.SANS, "OFL-1.1", new FontFile("nunito.woff2", "200 1000")),
    /** Diseñada para baja visión por el Braille Institute: buena opción de accesibilidad. */
    ATKINSON_HYPERLEGIBLE("Atkinson Hyperlegible", Category.SANS, "OFL-1.1",
            new FontFile("atkinson-hyperlegible-400.woff2", "400"),
            new FontFile("atkinson-hyperlegible-700.woff2", "700")),
    MERRIWEATHER("Merriweather", Category.SERIF, "OFL-1.1", new FontFile("merriweather.woff2", "300 900")),
    LORA("Lora", Category.SERIF, "OFL-1.1", new FontFile("lora.woff2", "400 700")),
    ROBOTO_SLAB("Roboto Slab", Category.SERIF, "Apache-2.0", new FontFile("roboto-slab.woff2", "100 900"));

    private final String family;
    private final Category category;
    private final String license;
    private final List<FontFile> files;

    FontCatalog(String family, Category category, String license, FontFile... files) {
        this.family = family;
        this.category = category;
        this.license = license;
        this.files = List.of(files);
    }

    /** Nombre tal como se guarda en {@link SiteDesign.Typography} y se usa en CSS. */
    public String family() {
        return family;
    }

    public Category category() {
        return category;
    }

    public String license() {
        return license;
    }

    public List<FontFile> files() {
        return files;
    }

    /** Pila CSS con respaldo del sistema mientras la fuente descarga (font-display: swap). */
    public String cssStack() {
        return "\"" + family + "\", " + category.fallback;
    }

    public static Optional<FontCatalog> byFamily(String family) {
        return Arrays.stream(values()).filter(f -> f.family.equals(family)).findFirst();
    }

    /** Archivo woff2 y rango de pesos que cubre ("100 900" en fuentes variables). */
    public record FontFile(String fileName, String weight) {
    }

    public enum Category {
        SANS("system-ui, -apple-system, \"Segoe UI\", Roboto, Arial, sans-serif"),
        SERIF("Georgia, \"Times New Roman\", serif");

        private final String fallback;

        Category(String fallback) {
            this.fallback = fallback;
        }
    }
}
