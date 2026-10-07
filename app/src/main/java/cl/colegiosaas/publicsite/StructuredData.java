package cl.colegiosaas.publicsite;

import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.info.FaqEntry;
import cl.colegiosaas.news.NewsArticle;
import cl.colegiosaas.platform.Address;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.site.FooterSettings;
import cl.colegiosaas.site.SocialLink;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos estructurados schema.org en JSON-LD (SEO-01): el colegio como {@code School}, los eventos, las noticias
 * y las preguntas frecuentes. Los buscadores los usan para mostrar fechas, dirección y respuestas en los resultados.
 */
@Component
public class StructuredData {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AppProperties app;
    private final SchoolTime time;

    StructuredData(AppProperties app, SchoolTime time) {
        this.app = app;
        this.time = time;
    }

    public String school(SiteContext site) {
        Map<String, Object> school = schoolNode(site);
        school.put("@context", "https://schema.org");
        return write(school);
    }

    public String event(Event event, SiteContext site) {
        Map<String, Object> node = context("Event");
        node.put("name", event.getTitle());
        node.put("startDate", iso(event.getStartsAt(), event.isAllDay()));
        node.put("endDate", iso(event.getEndsAt(), event.isAllDay()));
        node.put("eventStatus", "https://schema.org/EventScheduled");
        node.put("eventAttendanceMode", "https://schema.org/OfflineEventAttendanceMode");
        if (event.getDescription() != null) {
            node.put("description", event.getDescription());
        }
        Map<String, Object> place = node("Place");
        place.put("name", event.getLocation() != null ? event.getLocation() : site.schoolName());
        Map<String, Object> address = address(site.footer().address());
        if (address != null) {
            place.put("address", address);
        }
        node.put("location", place);
        node.put("organizer", reference(site));
        node.put("url", app.url("/calendario/" + event.getSlug()));
        return write(node);
    }

    public String article(NewsArticle article, SiteContext site, String imageUrl) {
        Map<String, Object> node = context("NewsArticle");
        node.put("headline", article.getTitle());
        if (article.getSummary() != null) {
            node.put("description", article.getSummary());
        }
        if (article.getPublishedAt() != null) {
            node.put("datePublished", article.getPublishedAt().toString());
        }
        if (article.getUpdatedAt() != null) {
            node.put("dateModified", article.getUpdatedAt().toString());
        }
        if (imageUrl != null) {
            node.put("image", List.of(imageUrl));
        }
        node.put("author", reference(site));
        node.put("publisher", reference(site));
        node.put("mainEntityOfPage", app.url("/noticias/" + article.getSlug()));
        return write(node);
    }

    public String faq(List<FaqEntry> entries) {
        Map<String, Object> node = context("FAQPage");
        node.put("mainEntity", entries.stream().map(entry -> {
            Map<String, Object> question = node("Question");
            question.put("name", entry.getQuestion());
            Map<String, Object> answer = node("Answer");
            answer.put("text", Jsoup.parse(entry.getAnswer() == null ? "" : entry.getAnswer()).text());
            question.put("acceptedAnswer", answer);
            return question;
        }).toList());
        return write(node);
    }

    private Map<String, Object> schoolNode(SiteContext site) {
        Map<String, Object> school = node("School");
        school.put("@id", app.url("/#colegio"));
        school.put("name", site.schoolName());
        school.put("url", app.url("/"));
        if (site.logo() != null) {
            school.put("logo", absolute(site.logo().src()));
        }
        FooterSettings footer = site.footer();
        Map<String, Object> address = address(footer.address());
        if (address != null) {
            school.put("address", address);
        }
        if (footer.address() != null && footer.address().latitude() != null && footer.address().longitude() != null) {
            Map<String, Object> geo = node("GeoCoordinates");
            geo.put("latitude", footer.address().latitude());
            geo.put("longitude", footer.address().longitude());
            school.put("geo", geo);
        }
        if (footer.phone() != null) {
            school.put("telephone", footer.phone());
        }
        if (footer.contactEmail() != null) {
            school.put("email", footer.contactEmail());
        }
        if (!footer.socialLinks().isEmpty()) {
            school.put("sameAs", footer.socialLinks().stream().map(SocialLink::url).toList());
        }
        return school;
    }

    /** El colegio como autor u organizador, enlazado al nodo principal por su @id. */
    private Map<String, Object> reference(SiteContext site) {
        Map<String, Object> school = node("School");
        school.put("@id", app.url("/#colegio"));
        school.put("name", site.schoolName());
        if (site.logo() != null) {
            Map<String, Object> logo = node("ImageObject");
            logo.put("url", absolute(site.logo().src()));
            school.put("logo", logo);
        }
        return school;
    }

    private static Map<String, Object> address(Address address) {
        if (address == null || address.street() == null) {
            return null;
        }
        Map<String, Object> node = node("PostalAddress");
        node.put("streetAddress", address.street());
        if (address.commune() != null) {
            node.put("addressLocality", address.commune());
        }
        if (address.region() != null) {
            node.put("addressRegion", address.region());
        }
        node.put("addressCountry", "CL");
        return node;
    }

    /** Con zona horaria del colegio; los eventos de día completo van solo con la fecha. */
    private String iso(LocalDateTime at, boolean allDay) {
        return allDay ? at.toLocalDate().toString()
                : at.atZone(time.zone()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String absolute(String path) {
        return path == null || path.startsWith("http") ? path : app.url(path);
    }

    private static Map<String, Object> context(String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("@context", "https://schema.org");
        node.put("@type", type);
        return node;
    }

    private static Map<String, Object> node(String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("@type", type);
        return node;
    }

    /** Se inserta dentro de un {@code <script>}: un "</" en un texto no puede cerrarlo. */
    private static String write(Object value) {
        return JSON.writeValueAsString(value).replace("</", "<\\/");
    }
}
