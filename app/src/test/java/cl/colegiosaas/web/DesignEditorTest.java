package cl.colegiosaas.web;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditLogRepository;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.site.HexColor;
import cl.colegiosaas.site.SiteDesignService;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Editor de marca: tokens con contraste AA verificado, borrador y publicación (CFG-02, CFG-03, ACC-01). */
class DesignEditorTest extends WebTestSupport {

    @Autowired
    SiteDesignService designs;

    @Autowired
    AuditLogRepository audit;

    UserAccount admin;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
    }

    @Test
    void editorShowsThemesAndTheContrastReview() throws Exception {
        mvc.perform(get("/admin/design").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Institucional")))
                .andExpect(content().string(containsString("Verificación de contraste")))
                .andExpect(content().string(containsString("Cumple")));
    }

    @Test
    void lowContrastPaletteIsRejectedAndNothingIsSaved() throws Exception {
        mvc.perform(tokens("#9ec5fe", "#ffffff", "#999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/design"))
                .andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(containsString("No cumple")));

        assertThat(designs.draft().palette().primary()).isEqualTo(HexColor.of("#1F3A5F"));
    }

    @Test
    void validChangesStayInTheDraftUntilPublished() throws Exception {
        mvc.perform(tokens("#0b3d2e", "#ffffff", "#1a1a1a")).andExpect(redirectedUrl("/admin/design"));

        assertThat(designs.draft().palette().primary()).isEqualTo(HexColor.of("#0B3D2E"));
        assertThat(designs.published().palette().primary()).isEqualTo(HexColor.of("#1F3A5F"));
        assertThat(designs.hasUnpublishedChanges()).isTrue();

        mvc.perform(post("/admin/design/publish").with(as(admin)).with(csrf())).andExpect(redirectedUrl("/admin/design"));

        assertThat(designs.published().palette().primary()).isEqualTo(HexColor.of("#0B3D2E"));
        assertThat(audit.findByActionOrderByIdAsc(AuditAction.PUBLISH)).anyMatch(e -> "SiteDesign".equals(e.getEntityType()));
        mvc.perform(get("/site/theme.css")).andExpect(content().string(containsString("--color-primary: #0B3D2E;")));
    }

    @Test
    void applyingAThemeReplacesTheDraftAndDiscardRestoresThePublishedOne() throws Exception {
        mvc.perform(post("/admin/design/theme").with(as(admin)).with(csrf()).param("theme", "friendly").param("variant", "sky"))
                .andExpect(redirectedUrl("/admin/design"));
        assertThat(designs.draft().theme()).isEqualTo("friendly");
        assertThat(designs.draft().typography().headingFont()).isEqualTo("Nunito");

        mvc.perform(post("/admin/design/discard").with(as(admin)).with(csrf()));
        assertThat(designs.draft().theme()).isEqualTo("classic");
    }

    @Test
    void unknownFontsCannotBeInjectedIntoTheStylesheet() throws Exception {
        mvc.perform(tokens("#1f3a5f", "#ffffff", "#1a1a1a", "x\"; } body { display:none"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    void onlyDesignersReachTheEditor() throws Exception {
        UserAccount editor = activeUser("editor@colegio.cl", Role.EDITOR);
        mvc.perform(get("/admin/design").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/design/publish").with(as(editor)).with(csrf())).andExpect(status().isForbidden());
    }

    /** Paleta completa con fondo y texto dados; cada parámetro una sola vez. */
    private MockHttpServletRequestBuilder tokens(String primary, String background, String text) {
        return tokens(primary, background, text, "Merriweather");
    }

    private MockHttpServletRequestBuilder tokens(String primary, String background, String text, String headingFont) {
        return post("/admin/design").with(as(admin)).with(csrf())
                .param("theme", "classic").param("variant", "navy")
                .param("primary", primary).param("secondary", "#2e5e4e").param("accent", "#c9962b")
                .param("background", background).param("surface", "#f5f3ee").param("text", text)
                .param("headingFont", headingFont).param("bodyFont", "Inter")
                .param("cornerRadius", "SMALL").param("shadow", "SOFT").param("colorScheme", "LIGHT");
    }
}
