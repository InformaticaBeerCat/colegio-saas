package cl.colegiosaas.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** Secreto de los vectores de prueba del RFC 6238 (ASCII "12345678901234567890"). */
    static final String RFC_SECRET = Base32.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    /** Vectores SHA1 del RFC 6238, Apéndice B, recortados a 6 dígitos (los últimos 6 de los 8 del RFC). */
    @ParameterizedTest
    @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037",
            "20000000000, 353130"
    })
    void matchesTheRfc6238TestVectors(long epochSeconds, String expected) {
        long step = Totp.stepAt(Instant.ofEpochSecond(epochSeconds));

        assertThat(Totp.codeAt(RFC_SECRET, step)).isEqualTo(expected);
    }

    @Test
    void acceptsTheNeighbourStepsButNotOlderOnes() {
        String secret = Totp.newSecret();
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        long step = Totp.stepAt(now);

        assertThat(Totp.matchingStep(secret, Totp.codeAt(secret, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.matchingStep(secret, Totp.codeAt(secret, step + 1), now)).hasValue(step + 1);
        assertThat(Totp.matchingStep(secret, Totp.codeAt(secret, step - 2), now)).isEmpty();
        assertThat(Totp.matchingStep(secret, "12ab56", now)).isEmpty();
    }

    @Test
    void otpauthUriCarriesIssuerAndAccount() {
        String uri = Totp.otpauthUri("Colegio San José", "ana@colegio.cl", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/Colegio%20San%20Jos%C3%A9:ana%40colegio.cl?secret=JBSWY3DPEHPK3PXP")
                .contains("&issuer=Colegio%20San%20Jos%C3%A9").contains("&digits=6&period=30");
    }

    @Test
    void qrIsAnSvgDataUri() {
        assertThat(QrCodes.svgDataUri("otpauth://totp/x")).startsWith("data:image/svg+xml;base64,");
    }
}
