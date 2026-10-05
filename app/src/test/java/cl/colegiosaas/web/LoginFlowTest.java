package cl.colegiosaas.web;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditLogEntry;
import cl.colegiosaas.audit.AuditLogRepository;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginFlowTest extends WebTestSupport {

    @Autowired
    AuditLogRepository audit;

    UserAccount superAdmin;

    @BeforeEach
    void setUp() {
        superAdmin = install();
    }

    @Test
    void anonymousVisitorsAreSentToTheLoginPage() throws Exception {
        mvc.perform(get("/admin")).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void mfaIsOptionalSoTheAdminEntersWithThePassword() throws Exception {
        MvcResult login = submitLogin("super@colegio.cl", PASSWORD);

        assertThat(login.getResponse().getRedirectedUrl()).isEqualTo("/admin");
        mvc.perform(get("/admin").session(sessionOf(login))).andExpect(status().isOk());
    }

    @Test
    void onceTurnedOnFromTheAccountTheCodeIsAskedAtLogin() throws Exception {
        MockHttpSession session = sessionOf(submitLogin("super@colegio.cl", PASSWORD));
        mvc.perform(get("/admin/mfa/enable").session(session)).andExpect(status().isOk());
        String secret = (String) session.getAttribute("mfa.pendingSecret");

        mvc.perform(post("/admin/mfa/enable").session(session).with(csrf()).param("code", "000000"))
                .andExpect(redirectedUrl("/admin/mfa/enable?error"));
        mvc.perform(post("/admin/mfa/enable").session(session).with(csrf()).param("code", nextCode(secret)))
                .andExpect(redirectedUrl("/admin/mfa/recovery-codes"));

        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) session.getAttribute("mfa.recoveryCodes");
        assertThat(codes).hasSize(10).allMatch(code -> code.matches("[2-9A-Z]{5}-[2-9A-Z]{5}"));
        mvc.perform(get("/admin/mfa/recovery-codes").session(session)).andExpect(status().isOk());

        // Desde ahora, la contraseña sola no basta.
        MockHttpSession next = sessionOf(submitLogin("super@colegio.cl", PASSWORD));
        mvc.perform(get("/admin").session(next)).andExpect(redirectedUrl("/admin/mfa/verify"));
    }

    @Test
    void enrolledUsersVerifyACodeThatCannotBeReused() throws Exception {
        String code = nextCode(enrollMfa("super@colegio.cl").secret());

        MvcResult first = submitLogin("super@colegio.cl", PASSWORD);
        assertThat(first.getResponse().getRedirectedUrl()).isEqualTo("/admin/mfa/verify");
        mvc.perform(post("/admin/mfa/verify").session(sessionOf(first)).with(csrf()).param("code", code))
                .andExpect(redirectedUrl("/admin"));

        MockHttpSession second = sessionOf(submitLogin("super@colegio.cl", PASSWORD));
        mvc.perform(post("/admin/mfa/verify").session(second).with(csrf()).param("code", code))
                .andExpect(redirectedUrl("/admin/mfa/verify?error"));
        mvc.perform(post("/admin/mfa/verify").session(second).with(csrf()).param("code", "000000"))
                .andExpect(redirectedUrl("/admin/mfa/verify?error"));
    }

    @Test
    void recoveryCodeLetsYouInOnce() throws Exception {
        String recovery = enrollMfa("super@colegio.cl").recoveryCodes().getFirst();

        MockHttpSession lostPhone = sessionOf(submitLogin("super@colegio.cl", PASSWORD));
        mvc.perform(post("/admin/mfa/verify").session(lostPhone).with(csrf()).param("code", recovery.toLowerCase()))
                .andExpect(redirectedUrl("/admin"));

        MockHttpSession again = sessionOf(submitLogin("super@colegio.cl", PASSWORD));
        mvc.perform(post("/admin/mfa/verify").session(again).with(csrf()).param("code", recovery))
                .andExpect(redirectedUrl("/admin/mfa/verify?error"));
    }

    @Test
    void turningMfaOffRequiresThePassword() throws Exception {
        String code = nextCode(enrollMfa("super@colegio.cl").secret());
        MockHttpSession session = sessionOf(submitLogin("super@colegio.cl", PASSWORD));
        mvc.perform(post("/admin/mfa/verify").session(session).with(csrf()).param("code", code));

        mvc.perform(post("/admin/mfa/disable").session(session).with(csrf()).param("currentPassword", "otra cosa"))
                .andExpect(flash().attributeExists("problem"));
        assertThat(users.findById(superAdmin.getId())).get().satisfies(u -> assertThat(u.isMfaEnabled()).isTrue());

        mvc.perform(post("/admin/mfa/disable").session(session).with(csrf()).param("currentPassword", PASSWORD))
                .andExpect(flash().attributeExists("notice"));
        assertThat(users.findById(superAdmin.getId())).get().satisfies(u -> assertThat(u.isMfaEnabled()).isFalse());
        assertThat(submitLogin("super@colegio.cl", PASSWORD).getResponse().getRedirectedUrl()).isEqualTo("/admin");
        assertThat(audit.findByActionOrderByIdAsc(AuditAction.MFA_DISABLED)).hasSize(1);
    }

    @Test
    void editorsWithoutMandatoryMfaGoStraightToThePanel() throws Exception {
        activeUser("editor@colegio.cl", Role.EDITOR);

        MvcResult login = submitLogin("editor@colegio.cl", PASSWORD);

        assertThat(login.getResponse().getRedirectedUrl()).isEqualTo("/admin");
        mvc.perform(get("/admin").session(sessionOf(login))).andExpect(status().isOk());
        assertThat(audit.findByActionOrderByIdAsc(AuditAction.LOGIN)).hasSize(1);
    }

    @Test
    void accountLocksAfterFiveWrongPasswords() throws Exception {
        activeUser("editor@colegio.cl", Role.EDITOR);
        for (int i = 0; i < 5; i++) {
            assertThat(submitLogin("editor@colegio.cl", "contraseña equivocada").getResponse().getRedirectedUrl())
                    .isEqualTo("/admin/login?error");
        }

        // Bloqueada: ni la contraseña correcta sirve (y el mensaje no lo delata).
        assertThat(submitLogin("editor@colegio.cl", PASSWORD).getResponse().getRedirectedUrl())
                .isEqualTo("/admin/login?error");
        assertThat(audit.findByActionOrderByIdAsc(AuditAction.ACCOUNT_LOCKED)).hasSize(1);
    }

    @Test
    void unknownEmailsGetTheSameAnswerAndAreNotStored() throws Exception {
        assertThat(submitLogin("nadie@ningunlado.cl", PASSWORD).getResponse().getRedirectedUrl())
                .isEqualTo("/admin/login?error");

        List<AuditLogEntry> failures = audit.findByActionOrderByIdAsc(AuditAction.LOGIN_FAILED);
        assertThat(failures).singleElement().satisfies(entry -> {
            assertThat(entry.getActorId()).isNull();
            assertThat(entry.getDetails()).doesNotContain("nadie@ningunlado.cl");
        });
    }

    @Test
    void formsRequireCsrfToken() throws Exception {
        mvc.perform(post("/admin/login").param("email", "super@colegio.cl").param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/admin/login"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"));
    }
}
