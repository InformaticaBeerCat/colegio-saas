package cl.colegiosaas.media;

/**
 * Lee la orientación EXIF (etiqueta 0x0112) de un JPEG. Los teléfonos guardan la foto "acostada" y
 * anotan cómo girarla; como al borrar el EXIF (MED-10) se pierde esa nota, hay que aplicar el giro antes.
 * Solo se lee esta etiqueta: el resto del EXIF (GPS, modelo de cámara) se descarta sin interpretarlo.
 */
final class ExifOrientation {

    static final int NORMAL = 1;

    private ExifOrientation() {
    }

    /** Valor de 1 a 8; {@link #NORMAL} si no hay EXIF o está dañado. */
    static int read(byte[] jpeg) {
        try {
            return find(jpeg);
        } catch (RuntimeException e) {
            return NORMAL;
        }
    }

    private static int find(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return NORMAL;
        }
        int pos = 2;
        while (pos + 4 < b.length && (b[pos] & 0xFF) == 0xFF) {
            int marker = b[pos + 1] & 0xFF;
            int length = u16(b, pos + 2, true);
            if (marker == 0xE1 && isExif(b, pos + 4)) {
                return fromTiff(b, pos + 10);
            }
            if (marker == 0xDA) {
                break; // comienzan los datos de la imagen: ya no hay más metadatos
            }
            pos += 2 + length;
        }
        return NORMAL;
    }

    private static boolean isExif(byte[] b, int at) {
        return at + 6 <= b.length && b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f';
    }

    private static int fromTiff(byte[] b, int tiff) {
        boolean bigEndian = b[tiff] == 'M';
        int ifd = tiff + (int) u32(b, tiff + 4, bigEndian);
        int entries = u16(b, ifd, bigEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (u16(b, entry, bigEndian) == 0x0112) {
                int value = u16(b, entry + 8, bigEndian);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int u16(byte[] b, int at, boolean bigEndian) {
        int b0 = b[at] & 0xFF;
        int b1 = b[at + 1] & 0xFF;
        return bigEndian ? (b0 << 8) | b1 : (b1 << 8) | b0;
    }

    private static long u32(byte[] b, int at, boolean bigEndian) {
        long high = u16(b, bigEndian ? at : at + 2, bigEndian);
        long low = u16(b, bigEndian ? at + 2 : at, bigEndian);
        return (high << 16) | low;
    }
}
