package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Instalación que exige MFA a los roles sensibles ({@code APP_ENFORCE_MFA=true}, USR-02). */
@TestPropertySource(properties = "app.security.enforce-mfa=true")
class MandatoryMfaTest extends WebTestSupport {

    @BeforeEach
    void setUp() {
        install();
    }

    @Test
    void sensitiveRolesMustEnrollBeforeUsingThePanel() throws Exception {
        MvcResult login = submitLogin("super@colegio.cl", PASSWORD);
        assertThat(login.getResponse().getRedirectedUrl()).isEqualTo("/admin/mfa/setup");
        MockHttpSession session = sessionOf(login);

        // Con la contraseña sola no se entra al panel: vuelve al paso que falta.
        mvc.perform(get("/admin").session(session)).andExpect(redirectedUrl("/admin/mfa/setup"));

        mvc.perform(get("/admin/mfa/setup").session(session)).andExpect(status().isOk());
        String secret = (String) session.getAttribute("mfa.pendingSecret");
        mvc.perform(post("/admin/mfa/setup").session(session).with(csrf()).param("code", nextCode(secret)))
                .andExpect(redirectedUrl("/admin/mfa/recovery-codes"));
        mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
    }

    @Test
    void sensitiveRolesCannotTurnItOff() throws Exception {
        activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        MockHttpSession session = sessionOf(submitLogin("directora@colegio.cl", PASSWORD));
        mvc.perform(get("/admin/mfa/setup").session(session));
        String secret = (String) session.getAttribute("mfa.pendingSecret");
        mvc.perform(post("/admin/mfa/setup").session(session).with(csrf()).param("code", nextCode(secret)));

        mvc.perform(post("/admin/mfa/disable").session(session).with(csrf()).param("currentPassword", PASSWORD))
                .andExpect(flash().attribute("problem", "Esta instalación exige la verificación en dos pasos para tu rol."));
    }

    @Test
    void otherRolesStayOptional() throws Exception {
        activeUser("editor@colegio.cl", Role.EDITOR);

        assertThat(submitLogin("editor@colegio.cl", PASSWORD).getResponse().getRedirectedUrl()).isEqualTo("/admin");
    }
}
