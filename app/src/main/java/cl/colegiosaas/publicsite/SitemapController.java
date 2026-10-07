package cl.colegiosaas.publicsite;

import cl.colegiosaas.shared.web.AppProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * {@code sitemap.xml} con todo lo público e indexable, y {@code robots.txt} que deja fuera el panel y los enlaces
 * personales (citas, inscripciones, estado de solicitudes) (SEO-02, SEO-03).
 */
@Controller
class SitemapController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final PublicContent content;
    private final AppProperties app;

    SitemapController(PublicContent content, AppProperties app) {
        this.content = content;
        this.app = app;
    }

    @GetMapping(value = "/sitemap.xml")
    ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        if (content.homePublished()) {
            url(xml, "/", null);
        }
        content.sectionPaths().forEach(path -> url(xml, path, null));
        content.items().stream()
                .filter(item -> !item.path().contains("#") && !"Sección".equals(item.section()))
                .forEach(item -> url(xml, item.path(), item.modified() == null ? null : DAY.format(item.modified())));
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(xml.toString());
    }

    @GetMapping(value = "/robots.txt")
    ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Disallow: /admin
                Disallow: /setup
                Disallow: /citas/
                Disallow: /inscripciones/
                Disallow: /privacidad/derechos/estado
                Disallow: /buscar

                Sitemap: %s
                """.formatted(app.url("/sitemap.xml"));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(body);
    }

    private void url(StringBuilder xml, String path, String lastModified) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(app.url(path), "UTF-8")).append("</loc>");
        if (lastModified != null) {
            xml.append("<lastmod>").append(lastModified).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}
