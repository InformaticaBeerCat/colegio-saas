package cl.colegiosaas.setup;

import cl.colegiosaas.admissions.AdmissionMode;
import cl.colegiosaas.admissions.AdmissionSettingsRepository;
import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditLogRepository;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.site.SiteSettingsRepository;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Primer arranque. Contexto propio y recién creado: la instalación recuerda que ya está hecha,
 * y estos tests necesitan partir sin instalar. La instalación exitosa va al final por lo mismo.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SetupWizardTest extends WebTestSupport {

    @Autowired
    SetupToken setupToken;

    @Autowired
    SchoolRepository schools;

    @Autowired
    SiteSettingsRepository siteSettings;

    @Autowired
    AdmissionSettingsRepository admissionSettings;

    @Autowired
    AuditLogRepository audit;

    @Test
    @Order(1)
    void everythingRedirectsToSetupUntilInstalled() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/setup"));
        mvc.perform(get("/admin")).andExpect(redirectedUrl("/setup"));
        mvc.perform(get("/admin/login")).andExpect(redirectedUrl("/setup"));
        mvc.perform(get("/css/admin.css")).andExpect(status().isOk());
        mvc.perform(get("/setup")).andExpect(status().isOk()).andExpect(view().name("setup/wizard"));
    }

    @Test
    @Order(2)
    void wrongTokenIsRejected() throws Exception {
        mvc.perform(form("TOKEN-FALSO", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "token"));

        assertThat(schools.count()).isZero();
    }

    @Test
    @Order(3)
    void weakPasswordIsRejected() throws Exception {
        mvc.perform(form(setupToken.value(), "corta"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "password"));
    }

    @Test
    @Order(4)
    void installationCreatesSchoolDefaultsAndSuperAdmin() throws Exception {
        mvc.perform(form(setupToken.value(), PASSWORD)).andExpect(redirectedUrl("/admin/login?installed"));

        assertThat(schools.findSingleton()).get().satisfies(school -> {
            assertThat(school.getName()).isEqualTo("Colegio Los Aromos");
            assertThat(school.isSetupCompleted()).isTrue();
        });
        assertThat(siteSettings.findSingleton()).isPresent();
        // Particular subvencionado: admite por el SAE.
        assertThat(admissionSettings.findSingleton()).get().extracting(s -> s.getMode()).isEqualTo(AdmissionMode.SAE);
        assertThat(users.findByEmail("directora@aromos.cl")).get().satisfies(admin -> {
            assertThat(admin.getRoles()).containsExactly(Role.SUPER_ADMIN);
            assertThat(admin.canLogIn()).isTrue();
            assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        });
        assertThat(audit.findByActionOrderByIdAsc(AuditAction.SETUP_COMPLETED)).hasSize(1);

        // Ya instalado: el asistente desaparece y el sitio responde.
        mvc.perform(get("/setup")).andExpect(status().isNotFound());
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
    }

    /** Cada parámetro una sola vez: .param() agrega valores, no los reemplaza. */
    private MockHttpServletRequestBuilder form(String token, String password) {
        return post("/setup").with(csrf())
                .param("token", token)
                .param("schoolName", "Colegio Los Aromos")
                .param("rbd", "12345-6")
                .param("dependency", "PRIVATE_SUBSIDIZED")
                .param("plan", "BASE")
                .param("contactEmail", "contacto@aromos.cl")
                .param("adminName", "Directora")
                .param("adminEmail", "directora@aromos.cl")
                .param("password", password)
                .param("passwordConfirmation", password);
    }
}
