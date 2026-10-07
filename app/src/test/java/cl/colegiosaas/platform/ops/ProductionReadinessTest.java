package cl.colegiosaas.platform.ops;

import cl.colegiosaas.platform.PlatformProperties;
import cl.colegiosaas.platform.license.LicenseService;
import cl.colegiosaas.shared.crypto.CryptoProperties;
import cl.colegiosaas.shared.web.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionReadinessTest {

    static final String KEY_A = "Kq3mR1pX0kUeQx2y4bN8f5Wj7cV9tL6sD1gH3aZ0oPw=";
    static final String KEY_B = "Zt7wQ2nB5yU8iO1pA4sD6fG9hJ3kL0xC2vB7nM5qWe0=";

    ProductionReadiness readiness(String environment, CryptoProperties crypto, String baseUrl, boolean secureCookies,
                                  LicenseService.Status license) {
        LicenseService licenses = mock(LicenseService.class);
        when(licenses.status()).thenReturn(license);
        MockEnvironment env = new MockEnvironment().withProperty("server.servlet.session.cookie.secure", String.valueOf(secureCookies));
        PlatformProperties platform = new PlatformProperties(environment, "", "", "", "", "", Duration.ofHours(26));
        return new ProductionReadiness(platform, crypto, new AppProperties(baseUrl, "no-responder@colegio.cl"), licenses, env);
    }

    static LicenseService.Status valid(String domain) {
        return new LicenseService.Status(LicenseService.State.VALID, cl.colegiosaas.platform.license.LicenseCodecTestAccess.license(domain), null);
    }

    @Test
    void developmentDefaultsDoNotStartInProduction() {
        ProductionReadiness unsafe = readiness("production", new CryptoProperties(ProductionReadiness.DEVELOPMENT_KEYS.get(0),
                ProductionReadiness.DEVELOPMENT_KEYS.get(1)), "http://localhost:8080", false,
                new LicenseService.Status(LicenseService.State.NONE, null, "La instalación no tiene licencia"));
        List<String> problems = unsafe.problems();
        assertThat(problems).hasSize(4);
        assertThat(String.join(" ", problems)).contains("llaves de desarrollo", "https://", "APP_SECURE_COOKIES", "licencia");
        assertThatThrownBy(unsafe::check).isInstanceOf(IllegalStateException.class).hasMessageContaining("no es apta para producción");

        // En desarrollo, lo mismo arranca sin reclamos.
        readiness("development", new CryptoProperties(ProductionReadiness.DEVELOPMENT_KEYS.get(0),
                ProductionReadiness.DEVELOPMENT_KEYS.get(1)), "http://localhost:8080", false,
                new LicenseService.Status(LicenseService.State.NONE, null, "x")).check();
    }

    @Test
    void aProperProductionSetupStartsAndTheLicenseMustMatchTheDomain() {
        ProductionReadiness ok = readiness("production", new CryptoProperties(KEY_A, KEY_B), "https://www.colegiosanjose.cl", true,
                valid("colegiosanjose.cl"));
        assertThat(ok.problems()).isEmpty();
        ok.check();

        ProductionReadiness otherDomain = readiness("production", new CryptoProperties(KEY_A, KEY_B), "https://otro.cl", true,
                valid("colegiosanjose.cl"));
        assertThat(otherDomain.problems()).singleElement().asString().contains("es para colegiosanjose.cl");

        ProductionReadiness sameKeys = readiness("production", new CryptoProperties(KEY_A, KEY_A), "https://colegiosanjose.cl", true,
                valid("colegiosanjose.cl"));
        assertThat(sameKeys.problems()).singleElement().asString().contains("distintas");
    }
}
