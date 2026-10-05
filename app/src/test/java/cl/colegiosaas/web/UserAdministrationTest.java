package cl.colegiosaas.web;

import cl.colegiosaas.identity.AccountService;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserStatus;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class UserAdministrationTest extends WebTestSupport {

    @Autowired
    AccountService accounts;

    UserAccount admin;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
    }

    @Test
    void editorsCannotOpenUserAdministrationNorAudit() throws Exception {
        UserAccount editor = activeUser("editor@colegio.cl", Role.EDITOR);

        mvc.perform(get("/admin/users").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(as(editor))).andExpect(status().isOk());
        mvc.perform(get("/admin/users").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/audit").with(as(admin))).andExpect(status().isOk());
    }

    @Test
    void guardiansCannotEnterThePanelAtAll() throws Exception {
        UserAccount guardian = activeUser("apoderado@mail.cl", Role.GUARDIAN);

        mvc.perform(get("/admin").with(as(guardian))).andExpect(status().isForbidden());
    }

    @Test
    void invitedPersonActivatesTheAccountFromTheEmailLink() throws Exception {
        mvc.perform(post("/admin/users/invite").with(as(admin)).with(csrf())
                        .param("name", "Pedro Soto").param("email", "Pedro@Colegio.cl").param("roles", "EDITOR"))
                .andExpect(redirectedUrl("/admin/users"));

        OutgoingMail invitation = mails().getFirst();
        assertThat(invitation.to()).isEqualTo("pedro@colegio.cl");
        String link = linkIn(invitation);

        mvc.perform(get(link)).andExpect(status().isOk()).andExpect(view().name("auth/set-password"));
        mvc.perform(post(link).with(csrf()).param("password", PASSWORD).param("passwordConfirmation", PASSWORD))
                .andExpect(redirectedUrl("/admin/login?activated"));

        assertThat(users.findByEmail("pedro@colegio.cl")).get()
                .satisfies(u -> assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE));
        assertThat(submitLogin("pedro@colegio.cl", PASSWORD).getResponse().getRedirectedUrl()).isEqualTo("/admin");

        // El enlace es de un solo uso.
        mvc.perform(get(link)).andExpect(view().name("auth/notice"));
    }

    @Test
    void schoolAdminsCannotGrantSuperAdmin() throws Exception {
        mvc.perform(post("/admin/users/invite").with(as(admin)).with(csrf())
                        .param("name", "Intruso").param("email", "x@colegio.cl").param("roles", "SUPER_ADMIN"))
                .andExpect(status().isForbidden());

        assertThat(users.existsByEmail("x@colegio.cl")).isFalse();
    }

    @Test
    void deactivationCutsOpenSessionsImmediately() throws Exception {
        UserAccount editor = activeUser("editor@colegio.cl", Role.EDITOR);
        MockHttpSession editorSession = sessionOf(submitLogin("editor@colegio.cl", PASSWORD));
        mvc.perform(get("/admin").session(editorSession)).andExpect(status().isOk());

        mvc.perform(post("/admin/users/{id}/deactivate", editor.getId()).with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/users/" + editor.getId()))
                .andExpect(flash().attributeExists("notice"));

        mvc.perform(get("/admin").session(editorSession)).andExpect(redirectedUrl("/admin/login?expired"));
        assertThat(submitLogin("editor@colegio.cl", PASSWORD).getResponse().getRedirectedUrl())
                .isEqualTo("/admin/login?error");
    }

    @Test
    void nobodyCanDeactivateThemselves() throws Exception {
        mvc.perform(post("/admin/users/{id}/deactivate", admin.getId()).with(as(admin)).with(csrf()))
                .andExpect(flash().attributeExists("problem"));

        assertThat(users.findById(admin.getId())).get().satisfies(u -> assertThat(u.canLogIn()).isTrue());
    }

    @Test
    void passwordResetDoesNotRevealWhichEmailsExist() throws Exception {
        activeUser("editor@colegio.cl", Role.EDITOR);

        mvc.perform(post("/admin/password/forgot").with(csrf()).param("email", "nadie@colegio.cl"))
                .andExpect(view().name("auth/notice"));
        assertThat(mails()).isEmpty();

        mvc.perform(post("/admin/password/forgot").with(csrf()).param("email", "editor@colegio.cl"))
                .andExpect(view().name("auth/notice"));
        String link = linkIn(mails().getFirst());

        String newPassword = "otra frase larga para entrar";
        mvc.perform(post(link).with(csrf()).param("password", newPassword).param("passwordConfirmation", newPassword))
                .andExpect(redirectedUrl("/admin/login?reset"));
        assertThat(submitLogin("editor@colegio.cl", newPassword).getResponse().getRedirectedUrl()).isEqualTo("/admin");
    }

    @Test
    void changingRolesTakesEffectOnTheNextLogin() throws Exception {
        UserAccount editor = activeUser("editor@colegio.cl", Role.EDITOR);
        MockHttpSession editorSession = sessionOf(submitLogin("editor@colegio.cl", PASSWORD));

        accounts.changeRoles(editor.getId(), Set.of(Role.SCHEDULE_MANAGER), admin.getId());

        mvc.perform(get("/admin").session(editorSession)).andExpect(redirectedUrl("/admin/login?expired"));
        assertThat(users.findById(editor.getId())).get()
                .satisfies(u -> assertThat(u.getRoles()).containsExactly(Role.SCHEDULE_MANAGER));
    }
}
