package cl.colegiosaas.page;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.html.HtmlSanitizer;
import cl.colegiosaas.shared.web.SafeUrls;
import cl.colegiosaas.site.SiteSection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Páginas armadas con bloques (PUB-01, CFG-04). Aquí viven las reglas que la tabla no puede expresar:
 * cada tipo salvo CUSTOM existe una sola vez, hay direcciones reservadas, el HTML se sanea y los
 * enlaces se revisan antes de guardar.
 */
@Service
public class PageService {

    /** Primeros tramos de URL que ya usa la aplicación o usarán las próximas fases. */
    static final Set<String> RESERVED_SLUGS = Set.of(
            "admin", "setup", "api", "actuator", "error", "site", "css", "js", "fonts", "images", "media",
            "login", "logout", "noticias", "calendario", "comunicados", "documentos", "galerias", "contacto",
            "admision", "agenda", "privacidad", "buscar", "sitemap", "robots", "favicon");

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_LIST_ITEMS = 12;

    private final PageRepository pages;
    private final MenuItemRepository menuItems;
    private final AuditTrail audit;

    PageService(PageRepository pages, MenuItemRepository menuItems, AuditTrail audit) {
        this.pages = pages;
        this.menuItems = menuItems;
        this.audit = audit;
    }

    /** La portada primero, luego por título. */
    @Transactional(readOnly = true)
    public List<Page> list() {
        return pages.findAll().stream()
                .sorted(Comparator.comparing((Page p) -> p.getKind() != PageKind.HOME)
                        .thenComparing(Page::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page get(long id) {
        return pages.findById(id).orElseThrow(() -> new NotFoundException("La página no existe"));
    }

    @Transactional(readOnly = true)
    public Optional<Page> home() {
        return pages.findFirstByKind(PageKind.HOME);
    }

    /** Página visible para el público; las no publicadas no existen para los visitantes. */
    @Transactional(readOnly = true)
    public Optional<Page> findPublished(String slug) {
        return pages.findBySlug(slug).filter(Page::isPublished);
    }

    @Transactional(readOnly = true)
    public Optional<Page> findBySlug(String slug) {
        return pages.findBySlug(slug);
    }

    /** Tipos que todavía se pueden crear: los únicos que no existen, más CUSTOM. */
    @Transactional(readOnly = true)
    public List<PageKind> creatableKinds() {
        return Arrays.stream(PageKind.values())
                .filter(kind -> kind == PageKind.CUSTOM || !pages.existsByKind(kind))
                .toList();
    }

    @Transactional
    public Page create(String title, String slug, PageKind kind) {
        if (kind != PageKind.CUSTOM && pages.existsByKind(kind)) {
            throw new PageException("Ya existe una página de ese tipo; edita la existente");
        }
        String cleanSlug = checkSlug(slug == null || slug.isBlank() ? slugify(title) : slug, null);
        Page page = pages.save(new Page(cleanSlug, requireTitle(title), kind));
        audit.record(AuditAction.CREATE, "Page", page.getId(), page.getTitle());
        return page;
    }

    @Transactional
    public void updateDetails(long id, PageDetails details) {
        Page page = get(id);
        page.setTitle(requireTitle(details.title()));
        page.setSlug(checkSlug(details.slug(), page));
        page.setSection(details.section() == null ? SiteSection.MAIN : details.section());
        String metaTitle = blankToNull(details.metaTitle());
        String metaDescription = blankToNull(details.metaDescription());
        page.setSeo(metaTitle == null && metaDescription == null ? null : new SeoMetadata(metaTitle, metaDescription));
        page.setNoindex(details.noindex());
        audit.record(AuditAction.UPDATE, "Page", id, page.getTitle());
    }

    @Transactional
    public void addBlock(long id, Block block) {
        Page page = get(id);
        List<Block> blocks = new ArrayList<>(page.getDraftBlocks());
        blocks.add(clean(block));
        page.editBlocks(blocks);
    }

    @Transactional
    public void replaceBlock(long id, int index, Block block) {
        Page page = get(id);
        List<Block> blocks = new ArrayList<>(page.getDraftBlocks());
        checkIndex(blocks, index);
        blocks.set(index, clean(block));
        page.editBlocks(blocks);
    }

    /** Mueve un bloque una posición hacia arriba (-1) o hacia abajo (+1); en los bordes no hace nada. */
    @Transactional
    public void moveBlock(long id, int index, int direction) {
        Page page = get(id);
        List<Block> blocks = new ArrayList<>(page.getDraftBlocks());
        checkIndex(blocks, index);
        int target = index + Integer.signum(direction);
        if (target < 0 || target >= blocks.size()) {
            return;
        }
        blocks.add(target, blocks.remove(index));
        page.editBlocks(blocks);
    }

    @Transactional
    public void removeBlock(long id, int index) {
        Page page = get(id);
        List<Block> blocks = new ArrayList<>(page.getDraftBlocks());
        checkIndex(blocks, index);
        blocks.remove(index);
        page.editBlocks(blocks);
    }

    @Transactional
    public void publish(long id) {
        Page page = get(id);
        page.publish();
        audit.record(AuditAction.PUBLISH, "Page", id, page.getTitle());
    }

    @Transactional
    public void unpublish(long id) {
        Page page = get(id);
        page.unpublish();
        audit.record(AuditAction.UNPUBLISH, "Page", id, page.getTitle());
    }

    @Transactional
    public void delete(long id) {
        Page page = get(id);
        if (page.getKind() == PageKind.HOME) {
            throw new PageException("La portada no se elimina; si no quieres mostrarla, despublícala");
        }
        if (menuItems.existsByPage(page)) {
            throw new PageException("La página está en un menú: sácala del menú antes de eliminarla");
        }
        pages.delete(page);
        audit.record(AuditAction.DELETE, "Page", id, page.getTitle());
    }

    /** "Proyecto Educativo (PEI)" → "proyecto-educativo-pei". */
    public static String slugify(String text) {
        if (text == null) {
            return "";
        }
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /**
     * Deja el bloque listo para guardar: HTML saneado, enlaces revisados y cantidades acotadas.
     * El {@code switch} sin {@code default} obliga a decidir qué revisar cuando se agrega un tipo nuevo.
     */
    static Block clean(Block block) {
        return switch (block) {
            case Block.Hero b -> new Block.Hero(b.title(), b.subtitle(), b.imageAssetId(), b.videoAssetId(),
                    b.buttonLabel(), optionalUrl(b.buttonUrl()));
            case Block.RichText b -> new Block.RichText(HtmlSanitizer.richText(b.html()));
            case Block.CallToAction b -> new Block.CallToAction(b.title(), b.text(), b.buttonLabel(), optionalUrl(b.buttonUrl()));
            case Block.LatestNews b -> new Block.LatestNews(b.title(), clampCount(b.count()));
            case Block.UpcomingEvents b -> new Block.UpcomingEvents(b.title(), clampCount(b.count()));
            case Block.Stats b -> new Block.Stats(b.title(), limit(b.items()));
            case Block.Testimonials b -> new Block.Testimonials(b.title(), limit(b.items()));
            case Block.Timeline b -> new Block.Timeline(b.title(), limit(b.entries()));
            case Block.QuickLinks b -> b;
            case Block.Gallery b -> b;
            case Block.Location b -> b;
            case Block.Faq b -> b;
        };
    }

    private String checkSlug(String slug, Page current) {
        String clean = slug == null ? "" : slug.strip().toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(clean).matches()) {
            throw new PageException("La dirección solo admite minúsculas, números y guiones (p. ej. quienes-somos)");
        }
        if (RESERVED_SLUGS.contains(clean)) {
            throw new PageException("La dirección /" + clean + " está reservada por el sistema; elige otra");
        }
        boolean takenByOther = pages.findBySlug(clean).filter(other -> other != current).isPresent();
        if (takenByOther) {
            throw new PageException("Ya existe una página con la dirección /" + clean);
        }
        return clean;
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new PageException("La página necesita un título");
        }
        return title.strip();
    }

    private static void checkIndex(List<Block> blocks, int index) {
        if (index < 0 || index >= blocks.size()) {
            throw new PageException("El bloque ya no existe; recarga la página");
        }
    }

    private static String optionalUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (!SafeUrls.isAllowed(url)) {
            throw new PageException("Enlace no permitido: usa una ruta del sitio (/admision) o una dirección https://");
        }
        return url.strip();
    }

    private static int clampCount(int count) {
        return Math.max(1, Math.min(MAX_LIST_ITEMS, count));
    }

    private static <T> List<T> limit(List<T> items) {
        return items.size() > MAX_LIST_ITEMS ? items.subList(0, MAX_LIST_ITEMS) : items;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Datos editables de una página, aparte de sus bloques. */
    public record PageDetails(String title, String slug, SiteSection section,
                              String metaTitle, String metaDescription, boolean noindex) {
    }
}
