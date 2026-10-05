package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.StructureService;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Niveles y cursos: la estructura que usan los filtros de noticias, calendario y comunicados. */
class StructureTest extends WebTestSupport {

    @Autowired
    StructureService structure;

    UserAccount admin;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
    }

    @Test
    void chileanLevelsLoadOnceAndCoursesCannotRepeat() throws Exception {
        mvc.perform(post("/admin/structure/levels/standard").with(as(admin)).with(csrf()));
        mvc.perform(post("/admin/structure/levels/standard").with(as(admin)).with(csrf()));
        assertThat(structure.levels()).hasSize(16).first().extracting(GradeLevel::getName).isEqualTo("Sala Cuna");

        long kinder = structure.levels().stream().filter(l -> l.getName().equals("Kínder")).findFirst().orElseThrow().getId();
        mvc.perform(post("/admin/structure/courses").with(as(admin)).with(csrf())
                .param("levelId", String.valueOf(kinder)).param("section", "a").param("year", "2027"));
        mvc.perform(post("/admin/structure/courses").with(as(admin)).with(csrf())
                        .param("levelId", String.valueOf(kinder)).param("section", "A").param("year", "2027"))
                .andExpect(flash().attribute("problem", containsString("ya existe")));
        assertThat(structure.courses(2027)).extracting(c -> c.displayName()).containsExactly("Kínder A");

        // Un nivel con cursos no se borra.
        mvc.perform(post("/admin/structure/levels/" + kinder + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("tiene cursos")));
    }

    @Test
    void onlySchoolSettingsCanEditTheStructure() throws Exception {
        UserAccount editor = activeUser("editor@colegio.cl", Role.EDITOR);
        mvc.perform(get("/admin/structure").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/structure").with(as(admin))).andExpect(status().isOk());
    }
}
