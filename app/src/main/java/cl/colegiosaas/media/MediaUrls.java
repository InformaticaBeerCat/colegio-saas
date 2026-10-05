package cl.colegiosaas.media;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Direcciones públicas de las fotos: {@code /medios/<sha256>/<ancho>.<formato>}. La huella no expone
 * ids ni se adivina; el controlador además verifica que la foto esté aprobada y no retirada.
 * En Thymeleaf: {@code ${@media.picture(foto)}}.
 */
@Component("media")
public class MediaUrls {

    public static final String PREFIX = "/medios/";
    public static final String PREVIEW_PREFIX = "/admin/media/files/";

    private static final Pattern YOUTUBE = Pattern.compile("(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{6,20})");
    private static final Pattern VIMEO = Pattern.compile("vimeo\\.com/(?:video/)?(\\d+)");

    /** Imagen lista para mostrar al público, o nulo si no se puede mostrar (pendiente, rechazada, retirada). */
    public ResponsiveImage picture(MediaAsset asset) {
        if (asset == null || asset.getKind() != MediaKind.IMAGE || !asset.isDisplayable()) {
            return null;
        }
        return build(asset, asset.publicFile(), PREFIX);
    }

    /** Vista para el panel: muestra también lo pendiente. {@code original} = sin difuminar (solo revisión). */
    public ResponsiveImage preview(MediaAsset asset, boolean original) {
        if (asset == null || asset.getKind() != MediaKind.IMAGE) {
            return null;
        }
        return build(asset, original ? asset.getFile() : asset.publicFile(), PREVIEW_PREFIX);
    }

    /** URL de la versión más cercana al ancho pedido (para el logo, Open Graph, miniaturas). */
    public String src(MediaAsset asset, int width) {
        ResponsiveImage image = picture(asset);
        if (image == null) {
            return null;
        }
        StoredFile file = asset.publicFile();
        return file.getVariants().stream()
                .filter(v -> v.width() >= width && !v.format().equals("webp"))
                .min(Comparator.comparingInt(StoredFile.ImageVariant::width))
                .map(v -> url(PREFIX, file, v))
                .orElse(image.src());
    }

    /**
     * Dirección para incrustar un video: YouTube en su dominio sin cookies de seguimiento y Vimeo en su
     * reproductor. Nulo si el video no se puede mostrar o la URL no es reconocible.
     */
    public String embed(MediaAsset asset) {
        if (asset == null || asset.getKind() != MediaKind.EMBEDDED_VIDEO || !asset.isDisplayable()) {
            return null;
        }
        Matcher youtube = YOUTUBE.matcher(asset.getEmbedUrl());
        if (youtube.find()) {
            return "https://www.youtube-nocookie.com/embed/" + youtube.group(1);
        }
        Matcher vimeo = VIMEO.matcher(asset.getEmbedUrl());
        return vimeo.find() ? "https://player.vimeo.com/video/" + vimeo.group(1) : null;
    }

    private static ResponsiveImage build(MediaAsset asset, StoredFile file, String prefix) {
        if (file == null || file.getWidth() == null) {
            return null;
        }
        List<StoredFile.ImageVariant> variants = file.getVariants();
        String webp = srcset(prefix, file, variants, true);
        String fallback = srcset(prefix, file, variants, false);
        String master = prefix + file.getSha256() + "/original." + extension(file);
        // La maestra es el respaldo de mayor tamaño.
        fallback = fallback.isEmpty() ? master + " " + file.getWidth() + "w" : fallback + ", " + master + " " + file.getWidth() + "w";
        String alt = asset.getAltText() == null ? "" : asset.getAltText();
        return new ResponsiveImage(alt, file.getWidth(), file.getHeight(), webp, fallback, master);
    }

    private static String srcset(String prefix, StoredFile file, List<StoredFile.ImageVariant> variants, boolean webp) {
        return variants.stream()
                .filter(v -> v.format().equals("webp") == webp)
                .sorted(Comparator.comparingInt(StoredFile.ImageVariant::width))
                .map(v -> url(prefix, file, v) + " " + v.width() + "w")
                .collect(Collectors.joining(", "));
    }

    private static String url(String prefix, StoredFile file, StoredFile.ImageVariant variant) {
        return prefix + file.getSha256() + "/" + variant.width() + "." + variant.format();
    }

    static String extension(StoredFile file) {
        return "image/png".equals(file.getContentType()) ? "png" : "jpg";
    }
}
