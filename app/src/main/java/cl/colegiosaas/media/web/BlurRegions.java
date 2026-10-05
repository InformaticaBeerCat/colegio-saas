package cl.colegiosaas.media.web;

import cl.colegiosaas.media.BlurRegion;
import cl.colegiosaas.shared.web.RuleViolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Zonas de difuminado como texto: "x,y,ancho,alto" en porcentajes, separadas por ";". El editor visual
 * escribe este campo; sin JavaScript se puede escribir a mano.
 */
final class BlurRegions {

    static final int MAX_REGIONS = 30;

    private BlurRegions() {
    }

    static List<BlurRegion> parse(String text) {
        List<BlurRegion> regions = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return regions;
        }
        for (String part : text.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            String[] numbers = part.strip().split("\\s*,\\s*");
            if (numbers.length != 4) {
                throw new RuleViolation("Cada zona lleva 4 números (x, y, ancho, alto en %): " + part.strip());
            }
            try {
                double x = Double.parseDouble(numbers[0]) / 100;
                double y = Double.parseDouble(numbers[1]) / 100;
                double w = Double.parseDouble(numbers[2]) / 100;
                double h = Double.parseDouble(numbers[3]) / 100;
                // Una zona que se sale un poco del borde se recorta en vez de rechazarse.
                x = clamp(x);
                y = clamp(y);
                regions.add(new BlurRegion(x, y, Math.min(w, 1 - x), Math.min(h, 1 - y)));
            } catch (IllegalArgumentException e) {
                throw new RuleViolation("Zona de difuminado inválida: " + part.strip());
            }
        }
        if (regions.size() > MAX_REGIONS) {
            throw new RuleViolation("Hasta " + MAX_REGIONS + " zonas por foto");
        }
        return regions;
    }

    static String format(List<BlurRegion> regions) {
        return regions.stream()
                .map(r -> String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", r.x() * 100, r.y() * 100, r.width() * 100, r.height() * 100))
                .collect(Collectors.joining("; "));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(0.999, value));
    }
}
