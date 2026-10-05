package cl.colegiosaas.site;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Color en formato "#RRGGBB". En JSON se guarda como texto plano: "#1F3A5F". */
public record HexColor(@JsonValue String value) {

    /** Va antes que las constantes: el constructor la usa al crearlas. */
    private static final Pattern FORMAT = Pattern.compile("#[0-9A-F]{6}");

    public static final HexColor WHITE = new HexColor("#FFFFFF");
    public static final HexColor BLACK = new HexColor("#000000");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public HexColor {
        value = Objects.requireNonNull(value, "color").toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Color inválido, se espera #RRGGBB: " + value);
        }
    }

    public static HexColor of(String value) {
        return new HexColor(value);
    }

    public static HexColor ofRgb(int red, int green, int blue) {
        return new HexColor("#%02X%02X%02X".formatted(clamp(red), clamp(green), clamp(blue)));
    }

    public int red() {
        return Integer.parseInt(value, 1, 3, 16);
    }

    public int green() {
        return Integer.parseInt(value, 3, 5, 16);
    }

    public int blue() {
        return Integer.parseInt(value, 5, 7, 16);
    }

    /** Mezcla con otro color: 0 deja este, 1 devuelve el otro. */
    public HexColor mix(HexColor other, double amount) {
        return ofRgb(
                (int) Math.round(red() + (other.red() - red()) * amount),
                (int) Math.round(green() + (other.green() - green()) * amount),
                (int) Math.round(blue() + (other.blue() - blue()) * amount));
    }

    /** Para {@code <input type="color">}, que solo acepta minúsculas. */
    public String lowercase() {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}
