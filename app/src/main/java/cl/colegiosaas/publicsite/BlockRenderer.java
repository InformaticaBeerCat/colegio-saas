package cl.colegiosaas.publicsite;

import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventDates;
import cl.colegiosaas.calendar.EventRepository;
import cl.colegiosaas.documents.DocumentService;
import cl.colegiosaas.info.FaqCategoryRepository;
import cl.colegiosaas.media.Album;
import cl.colegiosaas.media.AlbumService;
import cl.colegiosaas.media.MediaAssetRepository;
import cl.colegiosaas.media.MediaUrls;
import cl.colegiosaas.media.ResponsiveImage;
import cl.colegiosaas.info.FaqEntry;
import cl.colegiosaas.info.FaqEntryRepository;
import cl.colegiosaas.news.NewsArticle;
import cl.colegiosaas.news.NewsArticleRepository;
import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.BlockType;
import cl.colegiosaas.platform.Address;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.site.QuickLinkRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prepara los bloques de una página para mostrarlos (PUB-01). Los bloques que dependen de un módulo
 * no contratado (CFG-07) o que no tienen nada que mostrar se omiten, en vez de dejar una sección vacía.
 */
@Service
public class BlockRenderer {

    private static final int GALLERY_PREVIEW = 8;
    private static final Locale CHILE = Locale.forLanguageTag("es-CL");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d 'de' MMMM", CHILE);

    private final SchoolRepository schools;
    private final NewsArticleRepository news;
    private final EventRepository events;
    private final FaqEntryRepository faqEntries;
    private final FaqCategoryRepository faqCategories;
    private final QuickLinkRepository quickLinks;
    private final DocumentService documents;
    private final MediaAssetRepository assets;
    private final AlbumService albums;
    private final MediaUrls mediaUrls;
    private final Clock clock;

    BlockRenderer(SchoolRepository schools, NewsArticleRepository news, EventRepository events,
                  FaqEntryRepository faqEntries, FaqCategoryRepository faqCategories,
                  QuickLinkRepository quickLinks, DocumentService documents, MediaAssetRepository assets,
                  AlbumService albums, MediaUrls mediaUrls, Clock clock) {
        this.schools = schools;
        this.news = news;
        this.events = events;
        this.faqEntries = faqEntries;
        this.faqCategories = faqCategories;
        this.quickLinks = quickLinks;
        this.documents = documents;
        this.assets = assets;
        this.albums = albums;
        this.mediaUrls = mediaUrls;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RenderedBlock> render(List<Block> blocks) {
        School school = schools.findSingleton().orElse(null);
        List<RenderedBlock> rendered = new ArrayList<>();
        for (Block block : blocks) {
            RenderedBlock result = render(block, school);
            if (result != null) {
                rendered.add(result);
            }
        }
        return rendered;
    }

    private RenderedBlock render(Block block, School school) {
        String type = BlockType.of(block).key();
        return switch (block) {
            case Block.LatestNews b -> enabled(school, Feature.NEWS) ? withData(type, b, latestNews(b.count(), zone(school))) : null;
            case Block.UpcomingEvents b ->
                    enabled(school, Feature.CALENDAR) ? withData(type, b, upcomingEvents(b.count(), zone(school))) : null;
            case Block.QuickLinks b -> withData(type, b, quickLinks.findByActiveTrueOrderBySortOrderAsc());
            case Block.Faq b -> withData(type, b, faq(b.categoryId()));
            case Block.Location b -> school == null || school.getAddress() == null || isBlank(school.getAddress().street())
                    ? null : new RenderedBlock(type, b, location(school.getAddress()));
            case Block.Gallery b -> enabled(school, Feature.GALLERIES) && b.albumId() != null ? gallery(type, b) : null;
            case Block.Stats b -> b.items().isEmpty() ? null : new RenderedBlock(type, b, null);
            case Block.Testimonials b -> b.items().isEmpty() ? null : new RenderedBlock(type, b, null);
            case Block.Timeline b -> b.entries().isEmpty() ? null : new RenderedBlock(type, b, null);
            case Block.RichText b -> isBlank(b.html()) ? null : new RenderedBlock(type, b, null);
            case Block.Hero b -> new RenderedBlock(type, b, b.imageAssetId() == null ? null
                    : assets.findById(b.imageAssetId()).map(mediaUrls::picture).orElse(null));
            case Block.CallToAction b -> new RenderedBlock(type, b, null);
            case Block.Documents b -> withData(type, b, documents.published().stream()
                    .filter(d -> b.categories().isEmpty() || b.categories().contains(d.getCategory()))
                    .toList());
            case Block.ReportChannel b -> new RenderedBlock(type, b, null);
        };
    }

    /** Las primeras fotos aprobadas del álbum (solo si es público y está publicado). */
    private RenderedBlock gallery(String type, Block.Gallery block) {
        Album album = albums.publicAlbum(block.albumId());
        if (album == null) {
            return null;
        }
        List<ResponsiveImage> photos = album.visibleAssets().stream()
                .map(mediaUrls::picture)
                .filter(Objects::nonNull)
                .limit(GALLERY_PREVIEW)
                .toList();
        return photos.isEmpty() ? null : new RenderedBlock(type, block, new GalleryData(album.getTitle(), album.getSlug(), photos));
    }

    private List<NewsItem> latestNews(int count, ZoneId zone) {
        return news.findVisibleAt(clock.instant(), PageRequest.of(0, count)).stream()
                .map(n -> new NewsItem(n.getTitle(), n.getSummary(), date(n, zone), "/noticias/" + n.getSlug(),
                        mediaUrls.picture(n.getFeaturedImage())))
                .toList();
    }

    private static String date(NewsArticle article, ZoneId zone) {
        var when = article.getPublishedAt() != null ? article.getPublishedAt() : article.getPublishAt();
        return when == null ? null : DAY.format(when.atZone(zone));
    }

    private List<EventItem> upcomingEvents(int count, ZoneId zone) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(zone));
        return events.findPublishedBetween(now, now.plusYears(1)).stream()
                .limit(count)
                .map(e -> new EventItem(e.getTitle(), EventDates.when(e), e.getLocation(), e.getStartsAt().toLocalDate().toString(),
                        "/calendario/" + e.getSlug()))
                .toList();
    }

    private List<FaqEntry> faq(Long categoryId) {
        if (categoryId == null) {
            return faqEntries.findByPublishedTrueOrderByCategory_SortOrderAscSortOrderAsc();
        }
        return faqCategories.findById(categoryId)
                .map(faqEntries::findByCategoryAndPublishedTrueOrderBySortOrderAsc)
                .orElse(List.of());
    }

    private static LocationData location(Address address) {
        String text = Stream.of(address.street(), address.commune(), address.region())
                .filter(part -> !isBlank(part))
                .collect(Collectors.joining(", "));
        String mapUrl = address.latitude() != null && address.longitude() != null
                ? "https://www.openstreetmap.org/?mlat=%s&mlon=%s#map=17/%s/%s".formatted(
                address.latitude(), address.longitude(), address.latitude(), address.longitude())
                : "https://www.openstreetmap.org/search?query=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        return new LocationData(text, mapUrl);
    }

    /** Las listas vacías no se muestran: un bloque "Noticias" sin noticias no le sirve a nadie. */
    private static RenderedBlock withData(String type, Block block, List<?> data) {
        return data.isEmpty() ? null : new RenderedBlock(type, block, data);
    }

    private static boolean enabled(School school, Feature feature) {
        return school != null && school.hasFeature(feature);
    }

    private static ZoneId zone(School school) {
        return ZoneId.of(school == null ? "America/Santiago" : Objects.requireNonNullElse(school.getTimeZone(), "America/Santiago"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Noticia resumida para la portada, con el enlace a su página. */
    public record NewsItem(String title, String summary, String date, String href, ResponsiveImage image) {
    }

    /** @param isoDate fecha para el atributo {@code datetime} de {@code <time>} */
    public record EventItem(String title, String when, String location, String isoDate, String href) {
    }

    public record LocationData(String address, String mapUrl) {
    }

    public record GalleryData(String albumTitle, String albumSlug, List<ResponsiveImage> photos) {
    }
}
