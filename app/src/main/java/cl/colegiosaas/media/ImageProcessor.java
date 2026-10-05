package cl.colegiosaas.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Procesa las fotos al subirlas. Todo lo que se guarda pasa por aquí, y nada conserva los metadatos del
 * archivo original (MED-10): la imagen se decodifica, se gira según el EXIF y se vuelve a codificar desde
 * los píxeles, así que GPS, modelo de cámara y fecha exacta desaparecen.
 *
 * Genera una versión maestra (hasta 2560 px) y versiones por ancho en WebP y JPEG (MED-02) para que el
 * navegador baje la más liviana que le sirva. AVIF no tiene codificador Java confiable: queda pendiente.
 */
public final class ImageProcessor {

    public static final long MAX_BYTES = 15L * 1024 * 1024;
    /** Contra "bombas de descompresión": una imagen chica en bytes pero enorme en píxeles. */
    static final long MAX_PIXELS = 50_000_000L;
    static final int MASTER_SIZE = 2560;
    /** Anchos que pide el sitio: tarjetas en el teléfono, columnas y pantalla completa. */
    static final int[] WIDTHS = {480, 960, 1600};

    private ImageProcessor() {
    }

    public enum Format {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

        final String contentType;
        final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }
    }

    /** Imagen ya girada y sin metadatos, lista para generar versiones o difuminar. */
    public record Decoded(BufferedImage image, boolean transparent) {
    }

    /** Un archivo a guardar: la versión maestra o una variante. {@code width} es nulo en la maestra. */
    public record Encoded(Format format, Integer width, byte[] bytes) {
    }

    /** Resultado completo: la maestra con sus dimensiones y las variantes. */
    public record Processed(Encoded master, int width, int height, List<Encoded> variants) {
    }

    /** El tipo se decide por la firma del archivo, no por la extensión ni por lo que dice el navegador. */
    public static Format detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Format.PNG;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Format.WEBP;
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            throw new FileUploadException("Las fotos HEIC del iPhone no se aceptan todavía: expórtalas como JPG");
        }
        throw new FileUploadException("Formato no admitido: sube fotos JPG, PNG o WebP");
    }

    public static Decoded decode(byte[] content) {
        if (content.length == 0) {
            throw new FileUploadException("El archivo está vacío");
        }
        if (content.length > MAX_BYTES) {
            throw new FileUploadException("La foto supera los 15 MB");
        }
        Format format = detect(content);
        BufferedImage image = read(content);
        if (format == Format.JPEG) {
            image = orient(image, ExifOrientation.read(content));
        }
        return new Decoded(image, image.getColorModel().hasAlpha());
    }

    /** Maestra y variantes desde una imagen decodificada (o recién difuminada). */
    public static Processed process(Decoded decoded) {
        BufferedImage master = scaleToFit(decoded.image(), MASTER_SIZE);
        Format fallback = decoded.transparent() ? Format.PNG : Format.JPEG;
        List<Encoded> variants = new ArrayList<>();
        for (int width : WIDTHS) {
            if (width >= master.getWidth()) {
                break;
            }
            BufferedImage scaled = scaleToWidth(master, width);
            variants.add(new Encoded(Format.WEBP, width, encode(scaled, Format.WEBP, decoded.transparent())));
            variants.add(new Encoded(fallback, width, encode(scaled, fallback, decoded.transparent())));
        }
        // La maestra también tiene su WebP: es la versión grande que pide el navegador en pantallas anchas.
        variants.add(new Encoded(Format.WEBP, master.getWidth(), encode(master, Format.WEBP, decoded.transparent())));
        Encoded masterFile = new Encoded(fallback, null, encode(master, fallback, decoded.transparent()));
        return new Processed(masterFile, master.getWidth(), master.getHeight(), variants);
    }

    /**
     * Difumina las zonas de forma irreversible (MED-07): primero pixela con bloques grandes y luego suaviza,
     * así no queda información recuperable del rostro. Las zonas vienen en fracciones (0 a 1).
     */
    public static BufferedImage blur(BufferedImage source, List<BlurRegion> regions) {
        BufferedImage result = copy(source);
        for (BlurRegion region : regions) {
            int x = (int) Math.floor(region.x() * result.getWidth());
            int y = (int) Math.floor(region.y() * result.getHeight());
            int w = Math.max(1, Math.min(result.getWidth() - x, (int) Math.ceil(region.width() * result.getWidth())));
            int h = Math.max(1, Math.min(result.getHeight() - y, (int) Math.ceil(region.height() * result.getHeight())));
            int block = Math.max(8, Math.max(w, h) / 8);
            pixelate(result, x, y, w, h, block);
            pixelate(result, x, y, w, h, Math.max(4, block / 2));
        }
        return result;
    }

    // --- Lectura ---

    private static BufferedImage read(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new FileUploadException("No se pudo leer la imagen");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true); // ignorar metadatos al leer
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new FileUploadException("La imagen es demasiado grande (más de 50 megapíxeles)");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new FileUploadException("La imagen está dañada o incompleta");
        }
    }

    /**
     * Aplica las 8 orientaciones EXIF (espejos incluidos) copiando píxeles: para cada píxel de la imagen
     * resultante se calcula de qué píxel del original viene.
     */
    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        int outW = swap ? h : w;
        int outH = swap ? w : h;
        int[] in = image.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[outW * outH];
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                int sx;
                int sy;
                switch (orientation) {
                    case 2 -> { sx = w - 1 - x; sy = y; }          // espejo horizontal
                    case 3 -> { sx = w - 1 - x; sy = h - 1 - y; }  // 180°
                    case 4 -> { sx = x; sy = h - 1 - y; }          // espejo vertical
                    case 5 -> { sx = y; sy = x; }                  // transpuesta
                    case 6 -> { sx = y; sy = h - 1 - x; }          // 90° horario
                    case 7 -> { sx = w - 1 - y; sy = h - 1 - x; }  // transversal
                    default -> { sx = w - 1 - y; sy = x; }         // 8: 90° antihorario
                }
                out[y * outW + x] = in[sy * w + sx];
            }
        }
        BufferedImage result = new BufferedImage(outW, outH, imageType(image));
        result.setRGB(0, 0, outW, outH, out, 0, outW);
        return result;
    }

    // --- Escalado ---

    static BufferedImage scaleToFit(BufferedImage image, int maxSide) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= maxSide) {
            return copy(image);
        }
        return scaleToWidth(image, (int) Math.round(image.getWidth() * (double) maxSide / longest));
    }

    /** Reduce a la mitad por pasos y termina con bilineal: buena calidad sin dependencias. */
    static BufferedImage scaleToWidth(BufferedImage image, int width) {
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * (double) width / image.getWidth()));
        BufferedImage current = image;
        int w = image.getWidth();
        int h = image.getHeight();
        do {
            w = Math.max(width, w / 2);
            h = Math.max(targetHeight, h / 2);
            BufferedImage step = new BufferedImage(w, h, imageType(image));
            Graphics2D g = step.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = step;
        } while (w != width || h != targetHeight);
        return current;
    }

    // --- Escritura ---

    static byte[] encode(BufferedImage image, Format format, boolean transparent) {
        BufferedImage pixels = image;
        if (format == Format.JPEG && image.getColorModel().hasAlpha()) {
            pixels = flatten(image);
        }
        ImageWriter writer = ImageIO.getImageWritersByMIMEType(format.contentType).next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format != Format.PNG) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionType(param.getCompressionTypes()[0]);
                param.setCompressionQuality(format == Format.WEBP ? 0.80f : 0.85f);
            }
            if (format == Format.JPEG) {
                param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.write(null, new IIOImage(pixels, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** JPEG no tiene transparencia: se pinta sobre blanco. */
    private static BufferedImage flatten(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static void pixelate(BufferedImage image, int x0, int y0, int w, int h, int block) {
        for (int y = y0; y < y0 + h; y += block) {
            for (int x = x0; x < x0 + w; x += block) {
                int bw = Math.min(block, x0 + w - x);
                int bh = Math.min(block, y0 + h - y);
                long r = 0, g = 0, b = 0, a = 0;
                int count = bw * bh;
                for (int yy = y; yy < y + bh; yy++) {
                    for (int xx = x; xx < x + bw; xx++) {
                        int argb = image.getRGB(xx, yy);
                        a += (argb >>> 24) & 0xFF;
                        r += (argb >> 16) & 0xFF;
                        g += (argb >> 8) & 0xFF;
                        b += argb & 0xFF;
                    }
                }
                int avg = (int) (a / count) << 24 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
                for (int yy = y; yy < y + bh; yy++) {
                    for (int xx = x; xx < x + bw; xx++) {
                        image.setRGB(xx, yy, avg);
                    }
                }
            }
        }
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), imageType(image));
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    private static int imageType(BufferedImage image) {
        return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }
}
