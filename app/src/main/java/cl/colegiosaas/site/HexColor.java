package cl.colegiosaas.site;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Color en formato "#RRGGBB". En JSON se guarda como texto plano: "#1F3A5F". */
public record HexColor(@JsonValue String value) {

    private static final Pattern FORMAT = Pattern.compile("#[0-9A-F]{6}");

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
}
