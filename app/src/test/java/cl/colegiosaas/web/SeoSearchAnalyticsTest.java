package cl.colegiosaas.web;

import cl.colegiosaas.analytics.AnalyticsService;
import cl.colegiosaas.analytics.PageViewCounter;
import cl.colegiosaas.calendar.CalendarService;
import cl.colegiosaas.calendar.EventDraft;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.info.GeneralInfoService;
import cl.colegiosaas.news.NewsArticle;
import cl.colegiosaas.news.NewsArticleRepository;
import cl.colegiosaas.news.NewsService;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEO (SEO-01..03), búsqueda interna (UX-06) y conteo anónimo de visitas (REP-01). */
class SeoSearchAnalyticsTest extends WebTestSupport {

    static final String BROWSER = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130 Safari/537.36";

    @Autowired NewsService news;
    @Autowired NewsArticleRepository articles;
    @Autowired CalendarService calendar;
    @Autowired GeneralInfoService info;
    @Autowired PageViewCounter counter;
    @Autowired AnalyticsService analytics;
    @Autowired SchoolTime time;

    UserAccount admin;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
    }

    // --- SEO ---

    @Test
    void newsEventsAndFaqCarryStructuredDataAndOpenGraph() throws Exception {
        publishNews("Proceso de matrícula 2027", "<p>La matrícula comienza en diciembre.</p>");
        mvc.perform(get("/noticias/proceso-de-matricula-2027"))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"http://localhost:8080/noticias/proceso-de-matricula-2027\">")))
                .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"article\">")))
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"Proceso de matrícula 2027 · Colegio San José\">")))
                .andExpect(content().string(containsString("<script type=\"application/ld+json\">")))
                .andExpect(content().string(containsString("\"@type\":\"NewsArticle\"")))
                .andExpect(content().string(containsString("\"headline\":\"Proceso de matrícula 2027\"")));

        long event = calendar.create(new EventDraft("Acto aniversario", EventKind.CEREMONY, false, time.today().plusDays(5),
                LocalTime.of(10, 0), null, LocalTime.of(12, 0), "Gimnasio", "Con la banda del colegio", Set.of(), Set.of())).getId();
        calendar.publish(event);
        String slug = calendar.get(event).getSlug();
        mvc.perform(get("/calendario/" + slug))
                .andExpect(content().string(containsString("\"@type\":\"Event\"")))
                .andExpect(content().string(containsString("\"startDate\":\"" + time.today().plusDays(5) + "T10:00:00-0")))
                .andExpect(content().string(containsString("\"name\":\"Gimnasio\"")));

        long category = info.addFaqCategory("Uniforme").getId();
        info.saveFaqEntry(null, category, "¿Es obligatorio el uniforme?", "<p>Sí, según el <strong>Reglamento</strong>.</p>", true);
        mvc.perform(get("/preguntas-frecuentes"))
                .andExpect(content().string(containsString("\"@type\":\"FAQPage\"")))
                .andExpect(content().string(containsString("\"text\":\"Sí, según el Reglamento.\"")))
                .andExpect(content().string(containsString("id=\"pregunta-")));
    }

    @Test
    void textCannotBreakOutOfTheJsonLdScript() throws Exception {
        publishNews("Cierre </script><script>alert(1)</script>", "<p>x</p>");
        String slug = articles.findAll().getFirst().getSlug();
        String page = mvc.perform(get("/noticias/" + slug)).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("<\\/script>").doesNotContain("</script><script>alert(1)");
    }

    @Test
    void sitemapListsPublicContentAndRobotsKeepsPrivateLinksOut() throws Exception {
        publishNews("Feria científica", "<p>Proyectos de ciencia</p>");
        createDraft("Borrador secreto");

        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/xml")))
                .andExpect(content().string(containsString("<loc>http://localhost:8080/noticias/feria-cientifica</loc><lastmod>")))
                .andExpect(content().string(containsString("<loc>http://localhost:8080/documentos</loc>")))
                .andExpect(content().string(containsString("<loc>http://localhost:8080/agenda</loc>")))
                .andExpect(content().string(not(containsString("borrador-secreto"))))
                .andExpect(content().string(not(containsString("/admin"))));
        mvc.perform(get("/robots.txt"))
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Disallow: /citas/")))
                .andExpect(content().string(containsString("Sitemap: http://localhost:8080/sitemap.xml")));
    }

    @Test
    void privatePagesAreMarkedNoindex() throws Exception {
        mvc.perform(get("/admin/login")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mvc.perform(get("/citas/token-falso")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mvc.perform(get("/documentos")).andExpect(header().doesNotExist("X-Robots-Tag"));
        mvc.perform(get("/buscar").param("q", "algo"))
                .andExpect(content().string(containsString("noindex")))
                .andExpect(content().string(not(containsString("rel=\"canonical\""))));
    }

    // --- Búsqueda ---

    @Test
    void searchFindsPublishedContentIgnoringAccentsAndCase() throws Exception {
        publishNews("Proceso de matrícula 2027", "<p>Las familias deben presentar el certificado de nacimiento.</p>");
        publishNews("Campeonato de fútbol", "<p>Ganamos la final.</p>");
        createDraft("Matrícula secreta en borrador");
        long category = info.addFaqCategory("Admisión").getId();
        info.saveFaqEntry(null, category, "¿Cuándo es la matrícula?", "<p>En diciembre.</p>", true);

        mvc.perform(get("/buscar").param("q", "MATRICULA"))
                .andExpect(content().string(containsString("Proceso de matrícula 2027")))
                .andExpect(content().string(containsString("¿Cuándo es la matrícula?")))
                .andExpect(content().string(containsString("/preguntas-frecuentes#pregunta-")))
                .andExpect(content().string(not(containsString("Matrícula secreta"))))
                .andExpect(content().string(not(containsString("Campeonato de fútbol"))));
        mvc.perform(get("/buscar").param("q", "certificado nacimiento"))
                .andExpect(content().string(containsString("1 resultado(s)")))
                .andExpect(content().string(containsString("certificado de nacimiento")));
        // Las secciones fijas también se encuentran.
        mvc.perform(get("/buscar").param("q", "admision"))
                .andExpect(content().string(containsString("href=\"/admision\"")));
        mvc.perform(get("/buscar").param("q", "zzzz"))
                .andExpect(content().string(containsString("No encontramos resultados")));
        mvc.perform(get("/documentos")).andExpect(content().string(containsString("role=\"search\"")));
    }

    // --- Visitas ---

    @Test
    void visitsAreCountedAnonymouslyPerPageAndDay() throws Exception {
        mvc.perform(get("/documentos").header("User-Agent", BROWSER).header("Referer", "https://www.google.com/search?q=colegio"));
        mvc.perform(get("/documentos").header("User-Agent", BROWSER));
        mvc.perform(get("/documentos").header("User-Agent", BROWSER).header("DNT", "1"));
        mvc.perform(get("/documentos").header("User-Agent", "Googlebot/2.1 (+http://www.google.com/bot.html)"));
        mvc.perform(get("/no-existe").header("User-Agent", BROWSER));
        mvc.perform(get("/admin/login").header("User-Agent", BROWSER));
        mvc.perform(get("/sitemap.xml").header("User-Agent", BROWSER));

        AnalyticsService.Report report = analytics.report(30);
        assertThat(report.total()).isEqualTo(2);
        assertThat(report.pages()).containsExactly(new AnalyticsService.Count("/documentos", 2));
        assertThat(report.sources()).containsExactly(new AnalyticsService.Count("google.com", 1));
        assertThat(report.days()).hasSize(30);

        // Los totales se suman entre pasadas.
        mvc.perform(get("/documentos").header("User-Agent", BROWSER));
        assertThat(analytics.report(7).total()).isEqualTo(3);

        mvc.perform(get("/admin/analytics").with(as(admin)))
                .andExpect(content().string(containsString("3 visitas")))
                .andExpect(content().string(containsString("/documentos")))
                .andExpect(content().string(containsString("google.com")));
    }

    // --- Apoyo ---

    private long publishNews(String title, String body) throws Exception {
        long id = createDraft(title);
        mvc.perform(post("/admin/news/" + id).with(as(admin)).with(csrf()).param("title", title).param("body", body)
                .param("summary", "Resumen de " + title));
        news.approve(id, null, admin.getId());
        return id;
    }

    private long createDraft(String title) throws Exception {
        mvc.perform(post("/admin/news").with(as(admin)).with(csrf()).param("title", title));
        NewsArticle article = articles.findAll().stream().filter(a -> a.getTitle().equals(title)).findFirst().orElseThrow();
        return article.getId();
    }
}
