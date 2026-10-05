package cl.colegiosaas.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base32Test {

    /** Vectores del RFC 4648, sección 10, sin el relleno "=". */
    @ParameterizedTest
    @CsvSource({"f, MY", "fo, MZXQ", "foo, MZXW6", "foob, MZXW6YQ", "fooba, MZXW6YTB", "foobar, MZXW6YTBOI"})
    void encodesAndDecodesTheRfc4648Vectors(String plain, String encoded) {
        assertThat(Base32.encode(plain.getBytes(StandardCharsets.US_ASCII))).isEqualTo(encoded);
        assertThat(new String(Base32.decode(encoded), StandardCharsets.US_ASCII)).isEqualTo(plain);
    }

    @Test
    void decodingToleratesHowPeopleTypeSecrets() {
        assertThat(Base32.decode("mzxw 6ytb-oi==")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> Base32.decode("MZXW1")).isInstanceOf(IllegalArgumentException.class);
    }
}
