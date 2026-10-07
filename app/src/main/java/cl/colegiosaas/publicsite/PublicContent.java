package cl.colegiosaas.publicsite;

import cl.colegiosaas.calendar.CalendarService;
import cl.colegiosaas.documents.DocumentService;
import cl.colegiosaas.info.GeneralInfoService;
import cl.colegiosaas.media.AlbumService;
import cl.colegiosaas.news.NewsService;
import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Features;
import cl.colegiosaas.platform.SchoolTime;
import org.jsoup.Jsoup;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Todo lo que el sitio publica y puede indexarse: páginas, noticias, eventos, documentos, galerías y
 * preguntas frecuentes, con su texto plano. Lo usan el sitemap (SEO-02) y la búsqueda interna (UX-06).
 * Nada de lo privado (borradores, páginas marcadas sin indexar, álbumes de curso) aparece aquí.
 */
@Component
public class PublicContent {

    /** Cuántas noticias recientes entran al índice: un sitio escolar no publica miles al año. */
    private static final int MAX_NEWS = 1000;

    private final PageService pages;
    private final NewsService news;
    private final CalendarService calendar;
    private final DocumentService documents;
    private final AlbumService albums;
    private final GeneralInfoService info;
    private final Features features;
    private final SchoolTime time;

    PublicContent(PageService pages, NewsService news, CalendarService calendar, DocumentService documents,
                  AlbumService albums, GeneralInfoService info, Features features, SchoolTime time) {
        this.pages = pages;
        this.news = news;
        this.calendar = calendar;
        this.documents = documents;
        this.albums = albums;
        this.info = info;
        this.features = features;
        this.time = time;
    }

    /**
     * Un elemento publicado.
     *
     * @param section  qué es, para mostrarlo en los resultados ("Noticia", "Evento"…)
     * @param path     ruta pública
     * @param text     texto plano para buscar
     * @param modified última modificación, para el sitemap
     */
    public record Item(String section, String title, String path, String text, Instant modified) {
    }

    @Transactional(readOnly = true)
    public List<Item> items() {
        List<Item> items = new ArrayList<>(sections());
        for (Page page : pages.list()) {
            if (page.isPublished() && !page.isNoindex() && page.getKind() != PageKind.HOME) {
                items.add(new Item("Página", page.getTitle(), "/" + page.getSlug(),
                        join(page.getSeo() == null ? null : page.getSeo().metaDescription(), blocksText(page.getPublishedBlocks())),
                        page.getUpdatedAt()));
            }
        }
        if (features.on(Feature.NEWS)) {
            news.visible(null, null, PageRequest.of(0, MAX_NEWS)).forEach(article -> items.add(new Item("Noticia",
                    article.getTitle(), "/noticias/" + article.getSlug(), join(article.getSummary(), html(article.getBody())),
                    article.getUpdatedAt())));
        }
        if (features.on(Feature.CALENDAR)) {
            calendar.published(time.now().minusYears(1), time.now().plusYears(1), null, null).forEach(event -> items.add(
                    new Item("Evento", event.getTitle(), "/calendario/" + event.getSlug(),
                            join(event.getDescription(), event.getLocation()), event.getUpdatedAt())));
        }
        documents.published().forEach(document -> items.add(new Item("Documento", document.getTitle(),
                "/documentos/" + document.getSlug(), document.getDescription(), document.getUpdatedAt())));
        if (features.on(Feature.GALLERIES)) {
            albums.publicAlbums().forEach(album -> items.add(new Item("Galería", album.getTitle(),
                    "/galerias/" + album.getSlug(), album.getDescription(), album.getUpdatedAt())));
        }
        info.publishedFaq().forEach(entry -> items.add(new Item("Pregunta frecuente", entry.getQuestion(),
                "/preguntas-frecuentes#pregunta-" + entry.getId(), html(entry.getAnswer()), entry.getUpdatedAt())));
        return items;
    }

    /** Secciones fijas del sitio: aparecen en la búsqueda con una descripción de lo que contienen. */
    private List<Item> sections() {
        List<Item> sections = new ArrayList<>();
        section(sections, Feature.SAE_ADMISSIONS, "Admisión", "/admision", "Proceso de admisión postulación SAE vacantes fechas visitas guiadas matrícula");
        section(sections, Feature.SCHEDULING, "Agenda tu cita", "/agenda", "Agendar cita entrevista visita guiada reunión hora");
        section(sections, Feature.CONTACT, "Contacto", "/contacto", "Contacto teléfono correo dirección WhatsApp escribir mensaje secretaría");
        section(sections, Feature.CALENDAR, "Calendario", "/calendario", "Calendario escolar actos feriados vacaciones reuniones de apoderados");
        section(sections, Feature.NEWS, "Noticias", "/noticias", "Noticias actividades del colegio");
        section(sections, Feature.GALLERIES, "Galerías", "/galerias", "Fotos galerías actividades");
        sections.add(new Item("Sección", "Documentos institucionales", "/documentos",
                "Reglamento Interno protocolos Proyecto Educativo PEI plan de convivencia seguridad cuenta pública", null));
        sections.add(new Item("Sección", "Útiles, uniforme y minuta", "/informacion-practica", "Lista de útiles uniforme minuta alimentación", null));
        sections.add(new Item("Sección", "Talleres", "/talleres", "Talleres extraprogramáticos actividades deportes arte", null));
        sections.add(new Item("Sección", "Privacidad", "/privacidad", "Privacidad datos personales derechos cookies Ley 21.719", null));
        return sections;
    }

    private void section(List<Item> sections, Feature feature, String title, String path, String text) {
        if (features.on(feature)) {
            sections.add(new Item("Sección", title, path, text, null));
        }
    }

    /** Secciones fijas del sitio según los módulos activos, para el sitemap. */
    public List<String> sectionPaths() {
        List<String> paths = new ArrayList<>(List.of("/documentos", "/comunicados", "/preguntas-frecuentes", "/talleres",
                "/informacion-practica", "/privacidad"));
        if (features.on(Feature.NEWS)) {
            paths.add("/noticias");
        }
        if (features.on(Feature.CALENDAR)) {
            paths.add("/calendario");
        }
        if (features.on(Feature.GALLERIES)) {
            paths.add("/galerias");
        }
        if (features.on(Feature.CONTACT)) {
            paths.add("/contacto");
        }
        if (features.on(Feature.SCHEDULING)) {
            paths.add("/agenda");
        }
        if (features.on(Feature.SAE_ADMISSIONS)) {
            paths.add("/admision");
        }
        return paths;
    }

    @Transactional(readOnly = true)
    public boolean homePublished() {
        return pages.home().filter(Page::isPublished).isPresent();
    }

    static String blocksText(List<Block> blocks) {
        return blocks.stream().map(PublicContent::blockText).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    private static String blockText(Block block) {
        return switch (block) {
            case Block.Hero hero -> join(hero.title(), hero.subtitle());
            case Block.RichText rich -> html(rich.html());
            case Block.CallToAction cta -> join(cta.title(), cta.text());
            case Block.Stats stats -> join(stats.title(), stats.items() == null ? null
                    : stats.items().stream().map(s -> s.value() + " " + s.label()).collect(Collectors.joining(" ")));
            case Block.Testimonials testimonials -> join(testimonials.title(), testimonials.items() == null ? null
                    : testimonials.items().stream().map(Block.Testimonial::quote).collect(Collectors.joining(" ")));
            case Block.Timeline timeline -> join(timeline.title(), timeline.entries() == null ? null
                    : timeline.entries().stream().map(e -> join(e.year(), e.title(), e.text())).collect(Collectors.joining(" ")));
            case Block.ReportChannel channel -> join(channel.title(), channel.text());
            default -> null;
        };
    }

    private static String html(String html) {
        return html == null || html.isBlank() ? null : Jsoup.parse(html).text();
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" "));
    }
}
