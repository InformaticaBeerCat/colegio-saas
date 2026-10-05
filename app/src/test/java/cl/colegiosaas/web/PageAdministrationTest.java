package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.MenuLocation;
import cl.colegiosaas.page.MenuService;
import cl.colegiosaas.page.MenuService.MenuTarget;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageRepository;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Constructor de páginas por bloques desde el panel (PUB-01, CFG-04). */
class PageAdministrationTest extends WebTestSupport {

    @Autowired
    PageRepository pages;

    @Autowired
    PageService pageService;

    @Autowired
    MenuService menus;

    UserAccount editor;

    @BeforeEach
    void setUp() {
        install();
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
    }

    @Test
    void editorsCreatePagesWithASlugDerivedFromTheTitle() throws Exception {
        mvc.perform(post("/admin/pages").with(as(editor)).with(csrf())
                        .param("title", "Educación Física y Deportes").param("slug", "").param("kind", "CUSTOM"))
                .andExpect(status().is3xxRedirection());

        Page page = pages.findBySlug("educacion-fisica-y-deportes").orElseThrow();
        assertThat(page.isPublished()).isFalse();
        mvc.perform(get("/admin/pages").with(as(editor)))
                .andExpect(content().string(containsString("Educación Física y Deportes")));
    }

    @Test
    void reservedSlugsAndRepeatedKindsAreRejected() throws Exception {
        mvc.perform(post("/admin/pages").with(as(editor)).with(csrf())
                        .param("title", "Panel").param("slug", "admin").param("kind", "CUSTOM"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("problem", containsString("reservada")));

        pageService.create("Inicio", "inicio", PageKind.HOME);
        mvc.perform(post("/admin/pages").with(as(editor)).with(csrf())
                        .param("title", "Otra portada").param("slug", "otra").param("kind", "HOME"))
                .andExpect(model().attribute("problem", containsString("Ya existe una página de ese tipo")));
        assertThat(pageService.creatableKinds()).doesNotContain(PageKind.HOME).contains(PageKind.CUSTOM);
    }

    @Test
    void blocksAreEditedInTheDraftAndOnlyVisibleAfterPublishing() throws Exception {
        Page page = pageService.create("Nuestra historia", "historia", PageKind.CUSTOM);
        String base = "/admin/pages/" + page.getId();

        mvc.perform(get(base + "/blocks/new").param("type", "timeline").with(as(editor))).andExpect(status().isOk());
        mvc.perform(post(base + "/blocks").with(as(editor)).with(csrf())
                        .param("type", "timeline").param("title", "Hitos")
                        .param("items", "1985 | Fundación | Abrimos con tres cursos\n2001 | Jornada completa\n"))
                .andExpect(redirectedUrl(base + "#bloques"));
        mvc.perform(post(base + "/blocks").with(as(editor)).with(csrf())
                        .param("type", "rich-text")
                        .param("html", "<p>Somos un colegio <strong>laico</strong>.</p><script>alert('x')</script>"))
                .andExpect(redirectedUrl(base + "#bloques"));

        List<Block> blocks = pages.findById(page.getId()).orElseThrow().getDraftBlocks();
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).isInstanceOfSatisfying(Block.Timeline.class, timeline -> {
            assertThat(timeline.entries()).hasSize(2);
            assertThat(timeline.entries().get(0)).isEqualTo(new Block.TimelineEntry("1985", "Fundación", "Abrimos con tres cursos"));
            assertThat(timeline.entries().get(1).text()).isNull();
        });
        assertThat(blocks.get(1)).isEqualTo(new Block.RichText("<p>Somos un colegio <strong>laico</strong>.</p>"));

        // El texto queda arriba de la línea de tiempo.
        mvc.perform(post(base + "/blocks/1/move").with(as(editor)).with(csrf()).param("direction", "-1"));
        assertThat(pages.findById(page.getId()).orElseThrow().getDraftBlocks().get(0)).isInstanceOf(Block.RichText.class);

        mvc.perform(get("/historia")).andExpect(status().isNotFound());
        mvc.perform(post(base + "/publish").with(as(editor)).with(csrf())).andExpect(flash().attribute("notice", "Página publicada"));
        mvc.perform(get("/historia"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Nuestra historia</h1>")))
                .andExpect(content().string(containsString("Somos un colegio <strong>laico</strong>.")))
                .andExpect(content().string(containsString("Fundación")))
                .andExpect(content().string(not(containsString("alert('x')"))));

        // Un cambio posterior no se ve hasta volver a publicar.
        mvc.perform(post(base + "/blocks/1/delete").with(as(editor)).with(csrf()));
        mvc.perform(get("/historia")).andExpect(content().string(containsString("Fundación")));
    }

    @Test
    void dangerousLinksInButtonsAreRejected() throws Exception {
        Page page = pageService.create("Admisión 2027", null, PageKind.CUSTOM);

        mvc.perform(post("/admin/pages/" + page.getId() + "/blocks").with(as(editor)).with(csrf())
                        .param("type", "call-to-action").param("title", "Postula")
                        .param("buttonLabel", "Ir").param("buttonUrl", "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("problem", containsString("Enlace no permitido")));

        assertThat(pages.findById(page.getId()).orElseThrow().getDraftBlocks()).isEmpty();
    }

    @Test
    void pagesInAMenuAndTheHomeCannotBeDeleted() throws Exception {
        Page home = pageService.create("Inicio", "inicio", PageKind.HOME);
        Page about = pageService.create("Quiénes somos", "quienes-somos", PageKind.ABOUT);
        menus.add(MenuLocation.HEADER, new MenuTarget("Nosotros", about.getId(), null, null));

        mvc.perform(post("/admin/pages/" + about.getId() + "/delete").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("está en un menú")));
        mvc.perform(post("/admin/pages/" + home.getId() + "/delete").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("La portada no se elimina")));
        assertThat(pages.count()).isEqualTo(2);
    }

    @Test
    void missingPagesAnswerNotFoundAndUsersWithoutPermissionAreForbidden() throws Exception {
        mvc.perform(get("/admin/pages/999999").with(as(editor))).andExpect(status().isNotFound());
        UserAccount consentManager = activeUser("consentimientos@colegio.cl", Role.CONSENT_MANAGER);
        mvc.perform(get("/admin/pages").with(as(consentManager))).andExpect(status().isForbidden());
    }
}
