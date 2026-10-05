package cl.colegiosaas.page.web;

import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.BlockType;
import cl.colegiosaas.page.PageException;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Formulario de un bloque. Un solo formulario para todos los tipos: cada plantilla muestra solo los
 * campos de su tipo. Las listas (cifras, testimonios, hitos) se escriben una por línea, con sus partes
 * separadas por "|": así se editan sin JavaScript y con cualquier lector de pantalla.
 */
@Getter
@Setter
public class BlockForm {

    static final String SEPARATOR = "|";

    private String type;
    private String title;
    private String subtitle;
    private String text;
    private String html;
    private String buttonLabel;
    private String buttonUrl;
    private Integer count;
    private Long categoryId;
    private Long albumId;
    private String items;
    /** Se conservan al editar una portada; se eligen desde la biblioteca de medios (fase 5). */
    private Long imageAssetId;
    private Long videoAssetId;

    public static BlockForm empty(BlockType type) {
        BlockForm form = new BlockForm();
        form.type = type.key();
        form.count = 3;
        return form;
    }

    public static BlockForm of(Block block) {
        BlockForm form = new BlockForm();
        form.type = BlockType.of(block).key();
        switch (block) {
            case Block.Hero b -> {
                form.title = b.title();
                form.subtitle = b.subtitle();
                form.buttonLabel = b.buttonLabel();
                form.buttonUrl = b.buttonUrl();
                form.imageAssetId = b.imageAssetId();
                form.videoAssetId = b.videoAssetId();
            }
            case Block.RichText b -> form.html = b.html();
            case Block.QuickLinks b -> form.title = b.title();
            case Block.LatestNews b -> {
                form.title = b.title();
                form.count = b.count();
            }
            case Block.UpcomingEvents b -> {
                form.title = b.title();
                form.count = b.count();
            }
            case Block.Stats b -> {
                form.title = b.title();
                form.items = lines(b.items(), s -> join(s.value(), s.label()));
            }
            case Block.Testimonials b -> {
                form.title = b.title();
                form.items = lines(b.items(), t -> join(t.quote(), t.author(), t.role()));
            }
            case Block.CallToAction b -> {
                form.title = b.title();
                form.text = b.text();
                form.buttonLabel = b.buttonLabel();
                form.buttonUrl = b.buttonUrl();
            }
            case Block.Gallery b -> {
                form.title = b.title();
                form.albumId = b.albumId();
            }
            case Block.Timeline b -> {
                form.title = b.title();
                form.items = lines(b.entries(), e -> join(e.year(), e.title(), e.text()));
            }
            case Block.Location b -> form.title = b.title();
            case Block.Faq b -> {
                form.title = b.title();
                form.categoryId = b.categoryId();
            }
        }
        return form;
    }

    public BlockType blockType() {
        return BlockType.byKey(type).orElseThrow(() -> new PageException("Tipo de bloque desconocido"));
    }

    Block toBlock() {
        return switch (blockType()) {
            case HERO -> new Block.Hero(required(title, "La portada necesita un título"), blank(subtitle),
                    imageAssetId, videoAssetId, blank(buttonLabel), blank(buttonUrl));
            case RICH_TEXT -> new Block.RichText(html == null ? "" : html);
            case QUICK_LINKS -> new Block.QuickLinks(blank(title));
            case LATEST_NEWS -> new Block.LatestNews(blank(title), count == null ? 3 : count);
            case UPCOMING_EVENTS -> new Block.UpcomingEvents(blank(title), count == null ? 3 : count);
            case STATS -> new Block.Stats(blank(title), parse(parts -> new Block.Stat(parts[0], part(parts, 1)), 2));
            case TESTIMONIALS -> new Block.Testimonials(blank(title),
                    parse(parts -> new Block.Testimonial(parts[0], part(parts, 1), part(parts, 2)), 3));
            case CALL_TO_ACTION -> new Block.CallToAction(required(title, "El llamado necesita un título"), blank(text),
                    blank(buttonLabel), blank(buttonUrl));
            case GALLERY -> new Block.Gallery(blank(title), albumId);
            case TIMELINE -> new Block.Timeline(blank(title),
                    parse(parts -> new Block.TimelineEntry(parts[0], part(parts, 1), part(parts, 2)), 3));
            case LOCATION -> new Block.Location(blank(title));
            case FAQ -> new Block.Faq(blank(title), categoryId);
        };
    }

    private <T> List<T> parse(Function<String[], T> mapper, int maxParts) {
        if (items == null || items.isBlank()) {
            return List.of();
        }
        return items.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> Arrays.stream(line.split("\\" + SEPARATOR, maxParts)).map(String::strip).toArray(String[]::new))
                .map(mapper)
                .toList();
    }

    private static String part(String[] parts, int index) {
        return index < parts.length && !parts[index].isBlank() ? parts[index] : null;
    }

    private static <T> String lines(List<T> items, Function<T, String> line) {
        return items.stream().map(line).collect(Collectors.joining("\n"));
    }

    private static String join(String... parts) {
        int last = parts.length;
        while (last > 1 && (parts[last - 1] == null || parts[last - 1].isBlank())) {
            last--;
        }
        return Arrays.stream(parts, 0, last).map(p -> p == null ? "" : p).collect(Collectors.joining(" " + SEPARATOR + " "));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PageException(message);
        }
        return value.strip();
    }
}
