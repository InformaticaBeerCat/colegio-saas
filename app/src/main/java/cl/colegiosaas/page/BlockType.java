package cl.colegiosaas.page;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de bloque del constructor, con la misma clave que se guarda en el JSON ({@code "type":"hero"}).
 * El nombre visible está en {@code messages.properties} como {@code block.<clave>}.
 */
public enum BlockType {
    HERO("hero", Block.Hero.class, true),
    RICH_TEXT("rich-text", Block.RichText.class, true),
    QUICK_LINKS("quick-links", Block.QuickLinks.class, true),
    LATEST_NEWS("latest-news", Block.LatestNews.class, true),
    UPCOMING_EVENTS("upcoming-events", Block.UpcomingEvents.class, true),
    STATS("stats", Block.Stats.class, true),
    TESTIMONIALS("testimonials", Block.Testimonials.class, true),
    CALL_TO_ACTION("call-to-action", Block.CallToAction.class, true),
    /** Necesita servir las fotos del álbum: se habilita con la biblioteca de medios (fase 5). */
    GALLERY("gallery", Block.Gallery.class, false),
    TIMELINE("timeline", Block.Timeline.class, true),
    LOCATION("location", Block.Location.class, true),
    FAQ("faq", Block.Faq.class, true);

    private final String key;
    private final Class<? extends Block> blockClass;
    private final boolean available;

    BlockType(String key, Class<? extends Block> blockClass, boolean available) {
        this.key = key;
        this.blockClass = blockClass;
        this.available = available;
    }

    public String key() {
        return key;
    }

    /** Si se puede agregar desde el editor. Los no disponibles se conservan y renderizan si ya existen. */
    public boolean available() {
        return available;
    }

    public static BlockType of(Block block) {
        return Arrays.stream(values()).filter(t -> t.blockClass == block.getClass()).findFirst().orElseThrow();
    }

    public static Optional<BlockType> byKey(String key) {
        return Arrays.stream(values()).filter(t -> t.key.equals(key)).findFirst();
    }
}
