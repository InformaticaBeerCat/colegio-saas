package cl.colegiosaas.media;

import cl.colegiosaas.support.TestImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    @Test
    void formatIsDetectedFromTheFileSignatureNotTheName() {
        assertThat(ImageProcessor.detect(TestImages.jpeg(10, 10))).isEqualTo(ImageProcessor.Format.JPEG);
        assertThat(ImageProcessor.detect(TestImages.transparentPng(10, 10))).isEqualTo(ImageProcessor.Format.PNG);
        assertThatThrownBy(() -> ImageProcessor.detect("<html>hola</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(FileUploadException.class).hasMessageContaining("JPG, PNG o WebP");
        byte[] heic = new byte[16];
        System.arraycopy("ftypheic".getBytes(StandardCharsets.US_ASCII), 0, heic, 4, 8);
        assertThatThrownBy(() -> ImageProcessor.detect(heic)).hasMessageContaining("HEIC");
    }

    @Test
    void exifIsRemovedAndThePhotoIsRotatedFirst() throws IOException {
        byte[] withExif = TestImages.jpegWithExif(400, 200, 6, "GPS -33.4372,-70.6506 iPhone");
        assertThat(new String(withExif, StandardCharsets.ISO_8859_1)).contains("Exif", "GPS -33.4372");

        ImageProcessor.Processed processed = ImageProcessor.process(ImageProcessor.decode(withExif));

        String master = new String(processed.master().bytes(), StandardCharsets.ISO_8859_1);
        assertThat(master).doesNotContain("Exif").doesNotContain("-33.4372").doesNotContain("iPhone");
        // Orientación 6 = girar 90° a la derecha: la foto acostada queda vertical.
        assertThat(processed.width()).isEqualTo(200);
        assertThat(processed.height()).isEqualTo(400);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(processed.master().bytes()));
        // El rojo estaba a la izquierda; tras girar a la derecha queda arriba.
        assertThat(isRed(decoded.getRGB(100, 20))).isTrue();
        assertThat(isRed(decoded.getRGB(100, 380))).isFalse();
    }

    @Test
    void variantsAreCreatedInWebpAndJpegWithoutUpscaling() {
        ImageProcessor.Processed processed = ImageProcessor.process(ImageProcessor.decode(TestImages.jpeg(1200, 800)));

        assertThat(processed.master().format()).isEqualTo(ImageProcessor.Format.JPEG);
        assertThat(processed.variants()).extracting(v -> v.format() + "@" + v.width())
                .containsExactlyInAnyOrder("WEBP@480", "JPEG@480", "WEBP@960", "JPEG@960", "WEBP@1200");
        assertThat(processed.variants()).allSatisfy(v -> assertThat(v.bytes()).isNotEmpty());
    }

    @Test
    void bigPhotosAreReducedAndTransparentImagesKeepTheirAlpha() {
        ImageProcessor.Processed big = ImageProcessor.process(ImageProcessor.decode(TestImages.jpeg(4000, 3000)));
        assertThat(big.width()).isEqualTo(ImageProcessor.MASTER_SIZE);
        assertThat(big.height()).isEqualTo(1920);

        ImageProcessor.Processed logo = ImageProcessor.process(ImageProcessor.decode(TestImages.transparentPng(300, 300)));
        assertThat(logo.master().format()).isEqualTo(ImageProcessor.Format.PNG);
    }

    @Test
    void allEightExifOrientationsMapPixelsCorrectly() {
        // Imagen de 3x2 con píxeles distintos: (x, y) -> color único.
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                image.setRGB(x, y, 0x010101 * (1 + x + 3 * y));
            }
        }
        // Esperado según la especificación EXIF: dónde queda el píxel de arriba a la izquierda (valor 1).
        assertThat(ImageProcessor.orient(image, 1).getRGB(0, 0) & 0xFF).isEqualTo(1);
        assertThat(ImageProcessor.orient(image, 2).getRGB(2, 0) & 0xFF).isEqualTo(1);
        assertThat(ImageProcessor.orient(image, 3).getRGB(2, 1) & 0xFF).isEqualTo(1);
        assertThat(ImageProcessor.orient(image, 4).getRGB(0, 1) & 0xFF).isEqualTo(1);
        assertThat(ImageProcessor.orient(image, 5).getRGB(0, 0) & 0xFF).isEqualTo(1);
        assertThat(ImageProcessor.orient(image, 6).getRGB(1, 0) & 0xFF).isEqualTo(1);
        assertThat(ImageProcessor.orient(image, 7).getRGB(1, 2) & 0xFF).isEqualTo(1);
        assertThat(ImageProcessor.orient(image, 8).getRGB(0, 2) & 0xFF).isEqualTo(1);
        // Las orientaciones 5 a 8 intercambian ancho y alto.
        assertThat(ImageProcessor.orient(image, 6).getWidth()).isEqualTo(2);
        assertThat(ImageProcessor.orient(image, 6).getHeight()).isEqualTo(3);
    }

    @Test
    void blurOnlyChangesTheMarkedRegion() {
        BufferedImage stripes = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 100; y++) {
                stripes.setRGB(x, y, (x / 2) % 2 == 0 ? 0xFFFFFF : 0x000000);
            }
        }
        BufferedImage blurred = ImageProcessor.blur(stripes, List.of(new BlurRegion(0, 0, 0.5, 1)));

        // Dentro de la zona las rayas desaparecen (todo queda gris); fuera, no se tocan.
        assertThat(blurred.getRGB(10, 50)).isEqualTo(blurred.getRGB(11, 50)).isEqualTo(blurred.getRGB(12, 50));
        assertThat(blurred.getRGB(150, 50)).isEqualTo(stripes.getRGB(150, 50));
        assertThat(blurred.getRGB(152, 50)).isNotEqualTo(blurred.getRGB(150, 50));
    }

    @Test
    void decompressionBombsAreRejectedBeforeDecoding() throws IOException {
        assertThatThrownBy(() -> ImageProcessor.decode(pngHeaderOnly(20_000, 20_000)))
                .isInstanceOf(FileUploadException.class).hasMessageContaining("50 megapíxeles");
    }

    @Test
    void oversizedAndBrokenFilesAreRejected() {
        assertThatThrownBy(() -> ImageProcessor.decode(new byte[0])).hasMessageContaining("vacío");
        byte[] truncated = java.util.Arrays.copyOf(TestImages.jpeg(100, 100), 30);
        assertThatThrownBy(() -> ImageProcessor.decode(truncated)).isInstanceOf(FileUploadException.class);
    }

    private static boolean isRed(int argb) {
        Color c = new Color(argb);
        return c.getRed() > 200 && c.getBlue() < 60;
    }

    /** PNG que declara dimensiones enormes en su cabecera pero casi no trae datos. */
    private static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        byte[] ihdr = java.nio.ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        chunk(out, "IHDR", ihdr);
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        out.write(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
