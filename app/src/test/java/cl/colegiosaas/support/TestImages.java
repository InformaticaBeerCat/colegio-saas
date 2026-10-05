package cl.colegiosaas.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Imágenes de prueba generadas en memoria (no hay archivos binarios en el repositorio de tests). */
public final class TestImages {

    private TestImages() {
    }

    /** JPEG con la mitad izquierda roja y la derecha azul. */
    public static byte[] jpeg(int width, int height) {
        return write(twoColors(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    /** PNG con transparencia (como un logo). */
    public static byte[] transparentPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(20, 60, 120, 255));
        g.fillOval(0, 0, width, height);
        g.dispose();
        return write(image, "png");
    }

    /**
     * JPEG con un bloque EXIF: orientación {@code orientation} y un texto que simula coordenadas GPS.
     * Sirve para comprobar que el procesamiento gira la foto y descarta el EXIF (MED-10).
     */
    public static byte[] jpegWithExif(int width, int height, int orientation, String fakeGps) {
        byte[] jpeg = jpeg(width, height);
        byte[] gps = fakeGps.getBytes(StandardCharsets.US_ASCII);
        // TIFF little endian: cabecera (8) + IFD con 1 entrada (2 + 12 + 4) + texto.
        ByteBuffer tiff = ByteBuffer.allocate(8 + 18 + gps.length).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);
        tiff.put(gps);
        byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + exifHeader.length + tiff.capacity();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff.array());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    public static BufferedImage twoColors(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    public static byte[] write(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
