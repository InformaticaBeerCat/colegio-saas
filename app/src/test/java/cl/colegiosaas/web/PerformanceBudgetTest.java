package cl.colegiosaas.web;

import cl.colegiosaas.support.WebTestSupport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Presupuesto de rendimiento (UX-02) que se puede revisar sin navegador, en cada build: lo que hace lento o
 * inestable a un sitio casi siempre es peso, recursos que bloquean el pintado o imágenes sin tamaño. Las
 * métricas reales (LCP, CLS, TBT) las mide {@code perf/web-vitals.mjs} en CI.
 */
class PerformanceBudgetTest extends WebTestSupport {

    static final int MAX_HTML_BYTES = 60 * 1024;
    static final int MAX_SITE_CSS_BYTES = 40 * 1024;
    static final int MAX_JS_BYTES = 10 * 1024;
    static final List<String> PAGES = List.of("/documentos", "/calendario", "/noticias", "/agenda", "/contacto",
            "/admision", "/privacidad", "/preguntas-frecuentes", "/buscar?q=matricula");

    @BeforeEach
    void setUp() {
        install();
    }

    @Test
    void publicPagesStayLightAndDoNotBlockRendering() throws Exception {
        for (String url : PAGES) {
            String html = mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
            assertThat(html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).as("HTML de %s", url).isLessThan(MAX_HTML_BYTES);
            Document page = Jsoup.parse(html);
            assertThat(page.select("meta[name=viewport]")).as("viewport en %s", url).isNotEmpty();
            // Ningún script bloquea el pintado ni viene de otro dominio (solo la analítica externa, con consentimiento).
            for (Element script : page.select("script[src]")) {
                assertThat(script.hasAttr("defer") || script.hasAttr("async")).as("script %s en %s", script.attr("src"), url).isTrue();
                assertThat(script.attr("src")).as("script propio en %s", url).startsWith("/");
            }
            // Hojas de estilo solo del propio sitio: nada de fuentes ni CSS de terceros.
            page.select("link[rel=stylesheet]").forEach(link -> assertThat(link.attr("href")).startsWith("/"));
            // Imágenes con tamaño reservado (sin saltos de diseño, CLS).
            for (Element img : page.select("img")) {
                assertThat(img.hasAttr("width") && img.hasAttr("height")).as("tamaño de %s en %s", img.attr("src"), url).isTrue();
            }
        }
    }

    @Test
    void staticFilesAreSmallVersionedAndCached() throws Exception {
        Document page = Jsoup.parse(mvc.perform(get("/documentos")).andReturn().getResponse().getContentAsString());
        String css = page.select("link[rel=stylesheet][href^=/css/site]").attr("href");
        assertThat(css).as("la URL lleva la huella del contenido").matches("/css/site-[0-9a-f]{32}\\.css");
        MvcResult cssResult = mvc.perform(get(css)).andReturn();
        MockHttpServletResponse response = cssResult.getResponse();
        assertThat(response.getContentAsByteArray().length).isLessThan(MAX_SITE_CSS_BYTES);
        assertThat(response.getHeader("Cache-Control")).contains("max-age=31536000").contains("public");

        String js = page.select("script[src^=/js/site]").attr("src");
        assertThat(js).matches("/js/site-[0-9a-f]{32}\\.js");
        assertThat(mvc.perform(get(js)).andReturn().getResponse().getContentAsByteArray().length).isLessThan(MAX_JS_BYTES);

        // El tema se versiona por su diseño y también se guarda por un año.
        String theme = page.select("link[rel=stylesheet][href^=/site/theme.css]").attr("href");
        assertThat(mvc.perform(get(theme)).andReturn().getResponse().getHeader("Cache-Control")).contains("max-age=31536000");
    }
}
