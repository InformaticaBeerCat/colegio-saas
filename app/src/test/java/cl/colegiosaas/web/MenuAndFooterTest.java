package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.page.MenuItem;
import cl.colegiosaas.page.MenuLocation;
import cl.colegiosaas.page.MenuService;
import cl.colegiosaas.page.MenuService.MenuTarget;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageException;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Menú principal, del pie y datos de contacto (CFG-05). */
class MenuAndFooterTest extends WebTestSupport {

    @Autowired
    PageService pages;

    @Autowired
    MenuService menus;

    UserAccount admin;
    Page home;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        home = pages.create("Inicio", "inicio", PageKind.HOME);
        pages.publish(home.getId());
    }

    @Test
    void submenusShowOnlyPublishedPagesAndExternalLinksAreAnnounced() throws Exception {
        Page about = pages.create("Nosotros", "nosotros", PageKind.ABOUT);
        Page pei = pages.create("Proyecto Educativo", "proyecto-educativo", PageKind.PEI);
        pages.publish(pei.getId());

        mvc.perform(post("/admin/menus").with(as(admin)).with(csrf())
                        .param("menu", "HEADER").param("label", "Nosotros").param("targetType", "page").param("pageId", about.getId().toString()))
                .andExpect(redirectedUrl("/admin/menus?menu=HEADER"));
        MenuItem parent = menus.items(MenuLocation.HEADER).getFirst();
        mvc.perform(post("/admin/menus").with(as(admin)).with(csrf())
                .param("menu", "HEADER").param("label", "PEI").param("targetType", "page")
                .param("pageId", pei.getId().toString()).param("parentId", parent.getId().toString()));
        mvc.perform(post("/admin/menus").with(as(admin)).with(csrf())
                .param("menu", "HEADER").param("label", "Classroom").param("targetType", "url")
                .param("url", "https://classroom.google.com"));

        // "Nosotros" está en borrador: queda como rótulo del submenú, sin enlace.
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("<span class=\"menu__label\">Nosotros</span>")))
                .andExpect(content().string(not(containsString("href=\"/nosotros\""))))
                .andExpect(content().string(containsString("href=\"/proyecto-educativo\"")))
                .andExpect(content().string(containsString("aria-controls=\"submenu-" + parent.getId() + "\"")))
                .andExpect(content().string(containsString("(otro sitio)")));
    }

    @Test
    void menusHaveTwoLevelsAtMostAndRejectUnsafeLinks() throws Exception {
        MenuItem top = menus.add(MenuLocation.HEADER, new MenuTarget("Comunidad", null, "/comunidad", null));
        MenuItem child = menus.add(MenuLocation.HEADER, new MenuTarget("Centro de Padres", null, "/centro-de-padres", top.getId()));

        assertThatThrownBy(() -> menus.add(MenuLocation.HEADER, new MenuTarget("Nieto", null, "/x", child.getId())))
                .isInstanceOf(PageException.class).hasMessageContaining("dos niveles");
        assertThatThrownBy(() -> menus.add(MenuLocation.FOOTER, new MenuTarget("Otro menú", null, "/x", top.getId())))
                .isInstanceOf(PageException.class);
        mvc.perform(post("/admin/menus").with(as(admin)).with(csrf())
                        .param("menu", "HEADER").param("label", "Malo").param("targetType", "url").param("url", "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("problem", containsString("enlace válido")));
    }

    @Test
    void entriesCanBeReorderedAndDeletedWithTheirChildren() throws Exception {
        MenuItem first = menus.add(MenuLocation.FOOTER, new MenuTarget("Primero", null, "/a", null));
        MenuItem second = menus.add(MenuLocation.FOOTER, new MenuTarget("Segundo", null, "/b", null));
        menus.add(MenuLocation.FOOTER, new MenuTarget("Hijo", null, "/c", first.getId()));

        mvc.perform(post("/admin/menus/" + second.getId() + "/move").with(as(admin)).with(csrf()).param("direction", "-1"))
                .andExpect(redirectedUrl("/admin/menus?menu=FOOTER"));
        assertThat(menus.topLevel(MenuLocation.FOOTER)).extracting(MenuItem::getLabel).containsExactly("Segundo", "Primero");

        mvc.perform(post("/admin/menus/" + first.getId() + "/delete").with(as(admin)).with(csrf()));
        assertThat(menus.items(MenuLocation.FOOTER)).extracting(MenuItem::getLabel).containsExactly("Segundo");
    }

    @Test
    void footerShowsContactDataAndSocialNetworks() throws Exception {
        mvc.perform(post("/admin/footer").with(as(admin)).with(csrf())
                        .param("phone", "+56 2 2345 6789").param("contactEmail", "contacto@colegio.cl")
                        .param("whatsappNumber", "+56912345678")
                        .param("street", "Av. Los Aromos 1234").param("commune", "Ñuñoa").param("region", "Región Metropolitana")
                        .param("social[INSTAGRAM]", "https://instagram.com/colegio")
                        .param("footerText", "Colegio con reconocimiento oficial del Estado"))
                .andExpect(redirectedUrl("/admin/footer"));

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Av. Los Aromos 1234")))
                .andExpect(content().string(containsString("Ñuñoa, Región Metropolitana")))
                .andExpect(content().string(containsString("href=\"tel:+56223456789\"")))
                .andExpect(content().string(containsString("href=\"https://wa.me/56912345678\"")))
                .andExpect(content().string(containsString("href=\"https://instagram.com/colegio\"")))
                .andExpect(content().string(containsString("Colegio con reconocimiento oficial del Estado")));
    }

    @Test
    void socialLinksMustBeHttps() throws Exception {
        mvc.perform(post("/admin/footer").with(as(admin)).with(csrf()).param("social[FACEBOOK]", "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    void editorsManageMenusButNotTheFooter() throws Exception {
        UserAccount editor = activeUser("editor@colegio.cl", Role.EDITOR);
        mvc.perform(get("/admin/menus").with(as(editor))).andExpect(status().isOk());
        mvc.perform(get("/admin/footer").with(as(editor))).andExpect(status().isForbidden());
    }
}
