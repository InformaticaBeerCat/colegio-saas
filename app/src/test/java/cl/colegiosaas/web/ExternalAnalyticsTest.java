package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.privacy.web.CookiePreferences;
import cl.colegiosaas.support.WebTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Banner de cookies (PRV-03) con una herramienta de analítica externa configurada (REP-01): solo entonces hay
 * cookies opcionales que consentir, y el script se carga únicamente a quien las aceptó.
 */
@TestPropertySource(properties = "app.analytics.script-url=" + ExternalAnalyticsTest.SCRIPT)
class ExternalAnalyticsTest extends WebTestSupport {

    static final String SCRIPT = "https://analitica.example/script.js";

    @Autowired LegalTextService legalTexts;

    UserAccount admin;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
    }

    @Test
    void theCookieBannerAppearsUntilTheVisitorChoosesOnTheCurrentPolicy() throws Exception {
        mvc.perform(get("/privacidad")).andExpect(content().string(not(containsString("cookie-banner"))));
        legalTexts.publishFromTemplate(LegalTextKind.COOKIE_POLICY, admin.getId());

        mvc.perform(get("/documentos"))
                .andExpect(content().string(containsString("class=\"cookie-banner\"")))
                .andExpect(content().string(containsString("Solo necesarias")))
                .andExpect(content().string(containsString("value=\"/documentos\"")));

        // Sin token CSRF a propósito: el banner no abre sesión.
        MvcResult chosen = mvc.perform(post("/privacidad/cookies/preferencias").param("analitica", "false").param("volver", "/documentos"))
                .andExpect(redirectedUrl("/documentos"))
                .andExpect(header().string("Set-Cookie", containsString(CookiePreferences.COOKIE + "=1.0")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andReturn();
        assertThat(chosen.getRequest().getSession(false)).isNull();

        Cookie rejected = new Cookie(CookiePreferences.COOKIE, "1.0");
        mvc.perform(get("/documentos").cookie(rejected)).andExpect(content().string(not(containsString("class=\"cookie-banner\""))));
        mvc.perform(get("/privacidad/cookies").cookie(rejected))
                .andExpect(content().string(containsString("Solo usas las cookies necesarias")));
        mvc.perform(get("/privacidad/cookies").cookie(new Cookie(CookiePreferences.COOKIE, "1.1")))
                .andExpect(content().string(containsString("Aceptaste las cookies de analítica")));

        // No sirve para redirigir a otro sitio.
        mvc.perform(post("/privacidad/cookies/preferencias").param("analitica", "true").param("volver", "//malicioso.example"))
                .andExpect(redirectedUrl("/privacidad/cookies"));

        // Una política nueva vuelve a preguntar.
        LegalText v2 = legalTexts.openDraft(LegalTextKind.COOKIE_POLICY, false);
        legalTexts.publish(v2.getId(), admin.getId());
        mvc.perform(get("/documentos").cookie(rejected)).andExpect(content().string(containsString("class=\"cookie-banner\"")));
    }

    @Test
    void theExternalScriptLoadsOnlyAfterConsentAndIsAllowedByTheSecurityPolicy() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.COOKIE_POLICY, admin.getId());
        mvc.perform(get("/documentos"))
                .andExpect(content().string(not(containsString(SCRIPT))))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self' https://analitica.example")));
        mvc.perform(get("/documentos").cookie(new Cookie(CookiePreferences.COOKIE, "1.0")))
                .andExpect(content().string(not(containsString(SCRIPT))));
        mvc.perform(get("/documentos").cookie(new Cookie(CookiePreferences.COOKIE, "1.1")))
                .andExpect(content().string(containsString("src=\"" + SCRIPT + "\"")));
        // Ni en el panel.
        mvc.perform(get("/admin").with(as(admin)).cookie(new Cookie(CookiePreferences.COOKIE, "1.1")))
                .andExpect(content().string(not(containsString(SCRIPT))));
    }
}
