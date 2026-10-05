package cl.colegiosaas.site;

/**
 * Contraste de colores según WCAG 2.1 (ACC-01). AA exige 4,5:1 para texto normal y 3:1 para
 * texto grande y elementos de interfaz (bordes de campos, anillo de foco).
 */
public final class Contrast {

    /** Texto normal, nivel AA. */
    public static final double AA_TEXT = 4.5;

    /** Texto grande y componentes de interfaz, nivel AA. */
    public static final double AA_UI = 3.0;

    private Contrast() {
    }

    /** Razón de contraste entre 1 (iguales) y 21 (negro sobre blanco). */
    public static double ratio(HexColor a, HexColor b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Luminancia relativa: 0 para el negro, 1 para el blanco. */
    public static double luminance(HexColor color) {
        return 0.2126 * linear(color.red()) + 0.7152 * linear(color.green()) + 0.0722 * linear(color.blue());
    }

    /** Blanco o negro, el que se lea mejor sobre ese fondo (texto de botones y bandas de color). */
    public static HexColor readableOn(HexColor background) {
        return ratio(HexColor.WHITE, background) >= ratio(HexColor.BLACK, background) ? HexColor.WHITE : HexColor.BLACK;
    }

    /**
     * Acerca el color al blanco o al negro (según el fondo) lo justo para llegar al contraste pedido.
     * Sirve para derivar la versión oscura de una paleta sin perder su tono.
     */
    public static HexColor adjustToContrast(HexColor color, HexColor background, double required) {
        if (ratio(color, background) >= required) {
            return color;
        }
        HexColor target = readableOn(background);
        for (int step = 1; step <= 20; step++) {
            HexColor candidate = color.mix(target, step / 20.0);
            if (ratio(candidate, background) >= required) {
                return candidate;
            }
        }
        return target;
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
