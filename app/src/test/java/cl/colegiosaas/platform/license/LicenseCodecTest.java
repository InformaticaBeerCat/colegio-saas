package cl.colegiosaas.platform.license;

import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Plan;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseCodecTest {

    static KeyPair keys() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    static License license(LocalDate expires) {
        return new License("lic-1", "Colegio San José", "colegiosanjose.cl", Plan.COMMUNITY, Set.of(Feature.WHATSAPP_SMS),
                LocalDate.of(2026, 1, 1), expires);
    }

    @Test
    void aSignedLicenseVerifiesWithThePublicKeyAndKeepsItsData() throws Exception {
        KeyPair pair = keys();
        String token = LicenseCodec.sign(license(LocalDate.of(2027, 12, 31)), pair.getPrivate());

        License read = LicenseCodec.verify(token, pair.getPublic());
        assertThat(read.school()).isEqualTo("Colegio San José");
        assertThat(read.plan()).isEqualTo(Plan.COMMUNITY);
        assertThat(read.allowedFeatures()).contains(Feature.SCHEDULING, Feature.WHATSAPP_SMS).doesNotContain(Feature.PAYMENTS);

        // Las llaves viajan en Base64 (variable de entorno o archivo).
        String publicBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        assertThat(LicenseCodec.verify(token, LicenseCodec.publicKey(publicBase64)).id()).isEqualTo("lic-1");
    }

    @Test
    void anyChangeOrAnotherKeyInvalidatesIt() throws Exception {
        KeyPair pair = keys();
        String token = LicenseCodec.sign(license(LocalDate.of(2027, 12, 31)), pair.getPrivate());
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.replace("COMMUNITY", "ADMISSIONS_PRO").getBytes())
                + "." + token.split("\\.")[1];

        assertThatThrownBy(() -> LicenseCodec.verify(forged, pair.getPublic())).hasMessageContaining("firma");
        assertThatThrownBy(() -> LicenseCodec.verify(token, keys().getPublic())).hasMessageContaining("firma");
        assertThatThrownBy(() -> LicenseCodec.verify("basura", pair.getPublic())).hasMessageContaining("formato");
    }

    @Test
    void expiryHasAGracePeriod() {
        License license = license(LocalDate.of(2027, 3, 31));
        assertThat(license.isExpired(LocalDate.of(2027, 3, 31))).isFalse();
        assertThat(license.isExpired(LocalDate.of(2027, 4, 1))).isTrue();
        assertThat(license.isBeyondGrace(LocalDate.of(2027, 4, 30))).isFalse();
        assertThat(license.isBeyondGrace(LocalDate.of(2027, 5, 1))).isTrue();
    }
}
