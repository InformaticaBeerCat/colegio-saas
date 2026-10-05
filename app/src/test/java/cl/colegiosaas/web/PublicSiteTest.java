package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.page.PageRepository;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.site.AlertSeverity;
import cl.colegiosaas.site.SiteAlert;
import cl.colegiosaas.site.SiteAlertRepository;
import cl.colegiosaas.site.SiteDesignService;
import cl.colegiosaas.site.Theme;
import cl.colegiosaas.site.ThemeStylesheet;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Sitio público por bloques, asistente inicial y vista previa (CFG-01, PUB-01, CFG-05, CFG-08, ACC-04). */
class PublicSiteTest extends WebTestSupport {

    @Autowired
    PageService pages;

    @Autowired
    PageRepository pageRepository;

    @Autowired
    SiteDesignService designs;

    @Autowired
    SiteAlertRepository alerts;

    UserAccount admin;

    @BeforeEach
    void setUp() {
        admin = install();
    }

    @Test
    void beforeTheWizardTheHomeIsAnAccessibleComingSoonPage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/coming-soon"))
                .andExpect(content().string(containsString("<html lang=\"es-CL\"")))
                .andExpect(content().string(containsString("href=\"#contenido\"")))
                .andExpect(content().string(containsString("<main id=\"contenido\"")))
                .andExpect(content().string(containsString("<h1 class=\"hero__title\">Colegio San José</h1>")))
                .andExpect(content().string(containsString("class=\"theme-classic\"")));

        mvc.perform(get("/admin").with(as(admin))).andExpect(content().string(containsString("/admin/welcome")));
    }

    @Test
    void wizardPublishesThemeHomeAndMenuButKeepsInstitutionalPagesAsDrafts() throws Exception {
        runWizard("modern:ocean");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/page"))
                .andExpect(content().string(containsString("class=\"theme-modern\"")))
                .andExpect(content().string(containsString("<h1 class=\"hero__title\">Colegio San José</h1>")))
                .andExpect(content().string(containsString("aria-label=\"Principal\"")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                // Las páginas en borrador no aparecen en el menú ni existen para el público.
                .andExpect(content().string(not(containsString("Quiénes somos"))))
                .andExpect(content().string(containsString("<footer class=\"site-footer\">")));
        mvc.perform(get("/quienes-somos")).andExpect(status().isNotFound());
        mvc.perform(get("/inicio")).andExpect(redirectedUrl("/"));
        mvc.perform(get("/admin").with(as(admin))).andExpect(content().string(not(containsString("/admin/welcome\""))));

        pages.publish(pageRepository.findBySlug("quienes-somos").orElseThrow().getId());

        mvc.perform(get("/")).andExpect(content().string(containsString("href=\"/quienes-somos\"")));
        mvc.perform(get("/quienes-somos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Quiénes somos</h1>")))
                .andExpect(content().string(containsString("<title>Quiénes somos · Colegio San José</title>")));
    }

    @Test
    void themeStylesheetFollowsThePublishedDesignAndIsCachedByVersion() throws Exception {
        runWizard("friendly:sun");
        String version = ThemeStylesheet.of(Theme.FRIENDLY.design("sun")).version();

        mvc.perform(get("/")).andExpect(content().string(containsString("/site/theme.css?v=" + version)));
        mvc.perform(get("/site/theme.css").param("v", version))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(content().string(containsString("--color-primary: #B45309;")));
        mvc.perform(get("/site/theme.css").param("v", "antigua"))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void previewShowsDraftsAndTheDraftDesignOnlyToStaff() throws Exception {
        runWizard("classic:navy");
        designs.applyTheme(Theme.CLASSIC, "burgundy");

        mvc.perform(get("/admin/preview/quienes-somos").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vista previa del borrador")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex, nofollow\">")))
                .andExpect(content().string(containsString("Borrador: Cuenta la historia")))
                .andExpect(content().string(containsString("href=\"/admin/preview/quienes-somos\"")))
                .andExpect(content().string(containsString("/admin/preview-theme.css")));
        mvc.perform(get("/admin/preview-theme.css").with(as(admin)))
                .andExpect(content().string(containsString("--color-primary: #7A1F2B;")));
        // El público sigue viendo el diseño publicado.
        mvc.perform(get("/site/theme.css")).andExpect(content().string(containsString("--color-primary: #1F3A5F;")));

        mvc.perform(get("/admin/preview")).andExpect(redirectedUrl("/admin/login"));
        UserAccount consentManager = activeUser("consentimientos@colegio.cl", Role.CONSENT_MANAGER);
        mvc.perform(get("/admin/preview").with(as(consentManager))).andExpect(status().isForbidden());
    }

    @Test
    void visibleAlertsAppearOnEveryPage() throws Exception {
        SiteAlert alert = new SiteAlert("Mañana se suspenden las clases por lluvias", AlertSeverity.EMERGENCY);
        alert.activate();
        alerts.save(alert);

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("site-alert--emergency")))
                .andExpect(content().string(containsString("Mañana se suspenden las clases por lluvias")));
    }

    @Test
    void systemPathsAreNotTakenByPages() throws Exception {
        mvc.perform(get("/no-existe")).andExpect(status().isNotFound());
        mvc.perform(get("/css/site.css")).andExpect(status().isOk());
        mvc.perform(get("/js/site.js")).andExpect(status().isOk());
        mvc.perform(get("/fonts/inter.woff2")).andExpect(status().isOk());
    }

    private void runWizard(String choice) throws Exception {
        mvc.perform(post("/admin/welcome").with(as(admin)).with(csrf())
                        .param("choice", choice)
                        .param("createPages", "true"))
                .andExpect(redirectedUrl("/admin"));
    }
}
