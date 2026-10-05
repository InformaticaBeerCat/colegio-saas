package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.news.NewsArticle;
import cl.colegiosaas.news.NewsArticleRepository;
import cl.colegiosaas.news.NewsCategoryService;
import cl.colegiosaas.news.NewsService;
import cl.colegiosaas.news.NewsStatus;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.StructureService;
import cl.colegiosaas.support.WebTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Noticias: borrador → revisión → publicada o programada (NOT-01, NOT-02) y secciones satélite (PUB-07). */
class NewsWorkflowTest extends WebTestSupport {

    @Autowired
    NewsService news;

    @Autowired
    NewsArticleRepository articles;

    @Autowired
    NewsCategoryService categories;

    @Autowired
    StructureService structure;

    @Autowired
    SchoolRepository schools;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    UserAccount admin;
    UserAccount editor;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
    }

    @Test
    void editorWritesSubmitsAndTheReviewerPublishes() throws Exception {
        long id = createAndSave(editor, "Gran feria científica", "<p>Participaron <strong>40</strong> cursos.</p><script>x()</script>");

        mvc.perform(post("/admin/news/" + id + "/submit").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("notice", containsString("Enviada a revisión")));
        assertThat(mails()).anySatisfy(mail -> {
            assertThat(mail.to()).isEqualTo("directora@colegio.cl");
            assertThat(mail.subject()).contains("Gran feria científica");
        });
        // En revisión, quien escribe ya no puede cambiarla ni aprobarla.
        mvc.perform(post("/admin/news/" + id).with(as(editor)).with(csrf()).param("title", "Otro título"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pide que te la devuelvan")));
        mvc.perform(post("/admin/news/" + id + "/approve").with(as(editor)).with(csrf())).andExpect(status().isForbidden());

        mvc.perform(post("/admin/news/" + id + "/approve").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Noticia publicada"));

        mvc.perform(get("/noticias"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/noticias/gran-feria-cientifica\"")));
        mvc.perform(get("/noticias/gran-feria-cientifica"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Gran feria científica</h1>")))
                .andExpect(content().string(containsString("<strong>40</strong>")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void returnedNewsGoesBackToTheAuthorWithTheNote() throws Exception {
        long id = createAndSave(editor, "Aniversario", "<p>Texto</p>");
        news.submitForReview(id, editor.getId());

        mvc.perform(post("/admin/news/" + id + "/return").with(as(admin)).with(csrf()).param("note", "Falta la fecha del acto"));

        NewsArticle article = articles.findById(id).orElseThrow();
        assertThat(article.getStatus()).isEqualTo(NewsStatus.DRAFT);
        assertThat(article.getReviewNote()).isEqualTo("Falta la fecha del acto");
        assertThat(mails()).anySatisfy(mail -> {
            assertThat(mail.to()).isEqualTo("comunicaciones@colegio.cl");
            assertThat(mail.body()).contains("Falta la fecha del acto");
        });
    }

    @Test
    void scheduledNewsAppearsWhenItsTimeComes() throws Exception {
        long id = createAndSave(admin, "Inicio de clases 2027", "<p>Texto</p>");
        mvc.perform(post("/admin/news/" + id + "/approve").with(as(admin)).with(csrf()).param("publishAt", "2099-03-01T08:00"))
                .andExpect(flash().attribute("notice", "Noticia programada"));

        assertThat(articles.findById(id).orElseThrow().getStatus()).isEqualTo(NewsStatus.SCHEDULED);
        mvc.perform(get("/noticias/inicio-de-clases-2027")).andExpect(status().isNotFound());

        // Llega la hora: la tarea la pasa a publicada.
        em.flush();
        jdbc.update("update news_article set publish_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), id);
        em.clear();
        news.publishDue();

        assertThat(articles.findById(id).orElseThrow().getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        mvc.perform(get("/noticias/inicio-de-clases-2027")).andExpect(status().isOk());
    }

    @Test
    void satelliteEditorsOnlyWriteInTheirSection() throws Exception {
        UserAccount parents = activeUser("cepa@colegio.cl", Role.PARENTS_CENTER_EDITOR);
        long mainNews = createAndSave(editor, "Noticia del colegio", "<p>x</p>");

        mvc.perform(post("/admin/news").with(as(parents)).with(csrf()).param("title", "Bingo solidario").param("section", "MAIN"))
                .andExpect(flash().attribute("problem", containsString("No puedes publicar en esa sección")));
        mvc.perform(post("/admin/news").with(as(parents)).with(csrf()).param("title", "Bingo solidario"));
        assertThat(articles.findBySlug("bingo-solidario").orElseThrow().getSection().name()).isEqualTo("PARENTS_CENTER");

        mvc.perform(get("/admin/news/" + mainNews).with(as(parents))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/news").with(as(parents))).andExpect(content().string(not(containsString("Noticia del colegio"))));
    }

    @Test
    void publicListFiltersByCategoryAndGradeLevel() throws Exception {
        structure.loadChileanLevels();
        GradeLevel first = structure.levels().stream().filter(l -> l.getName().equals("1° Básico")).findFirst().orElseThrow();
        long sports = categories.add("Deportes").getId();

        long a = createAndSave(admin, "Campeonato de fútbol", "<p>x</p>");
        mvc.perform(post("/admin/news/" + a).with(as(admin)).with(csrf())
                .param("title", "Campeonato de fútbol").param("body", "<p>x</p>").param("categoryId", String.valueOf(sports))
                .param("gradeLevelIds", String.valueOf(first.getId())));
        long b = createAndSave(admin, "Muestra de arte", "<p>x</p>");
        news.approve(a, null, admin.getId());
        news.approve(b, null, admin.getId());

        mvc.perform(get("/noticias").param("categoria", "deportes"))
                .andExpect(content().string(containsString("Campeonato de fútbol")))
                .andExpect(content().string(not(containsString("Muestra de arte"))));
        // "Muestra de arte" no tiene niveles: es para todo el colegio y aparece con cualquier nivel.
        mvc.perform(get("/noticias").param("nivel", "1-basico"))
                .andExpect(content().string(containsString("Campeonato de fútbol")))
                .andExpect(content().string(containsString("Muestra de arte")));
        mvc.perform(get("/noticias").param("nivel", "2-basico"))
                .andExpect(content().string(not(containsString("Campeonato de fútbol"))));
    }

    @Test
    void newsDisappearWhenTheModuleIsNotContracted() throws Exception {
        schools.findSingleton().orElseThrow().disableFeature(Feature.NEWS);
        mvc.perform(get("/noticias")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/news").with(as(admin))).andExpect(status().isNotFound());
    }

    @Test
    void publishedNewsCannotBeDeletedOnlyArchived() throws Exception {
        long id = createAndSave(admin, "Para archivar", "<p>x</p>");
        news.approve(id, null, admin.getId());

        mvc.perform(post("/admin/news/" + id + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("se archivan")));
        mvc.perform(post("/admin/news/" + id + "/archive").with(as(admin)).with(csrf()));
        mvc.perform(get("/noticias/para-archivar")).andExpect(status().isNotFound());
    }

    private long createAndSave(UserAccount user, String title, String body) throws Exception {
        mvc.perform(post("/admin/news").with(as(user)).with(csrf()).param("title", title));
        NewsArticle article = articles.findAll().stream().filter(a -> a.getTitle().equals(title)).findFirst().orElseThrow();
        mvc.perform(post("/admin/news/" + article.getId()).with(as(user)).with(csrf())
                .param("title", title).param("body", body).param("summary", "Resumen de " + title));
        return article.getId();
    }
}
