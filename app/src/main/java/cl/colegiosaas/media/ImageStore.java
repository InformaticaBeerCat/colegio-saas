package cl.colegiosaas.media;

import cl.colegiosaas.shared.storage.FileStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda una imagen procesada: la maestra y sus variantes, con llaves derivadas de la huella
 * ({@code media/2026/10/<sha256>.jpg}, {@code …-960.webp}). Una foto subida dos veces se guarda una vez.
 */
@Component
public class ImageStore {

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final Clock clock;

    ImageStore(StoredFileRepository files, FileStorage storage, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.clock = clock;
    }

    public StoredFile store(ImageProcessor.Processed image, String originalName) {
        String sha256 = Hashes.sha256(image.master().bytes());
        return files.findFirstBySha256(sha256).orElseGet(() -> {
            YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
            String base = "media/%d/%02d/%s".formatted(month.getYear(), month.getMonthValue(), sha256);
            String masterKey = base + "." + image.master().format().extension();
            storage.put(masterKey, image.master().bytes());
            List<StoredFile.ImageVariant> variants = new ArrayList<>();
            for (ImageProcessor.Encoded variant : image.variants()) {
                String key = base + "-" + variant.width() + "." + variant.format().extension();
                storage.put(key, variant.bytes());
                variants.add(new StoredFile.ImageVariant(variant.format().extension(), variant.width(), key, variant.bytes().length));
            }
            StoredFile file = new StoredFile(masterKey, cleanName(originalName, image.master().format()),
                    image.master().format().contentType(), image.master().bytes().length, sha256);
            file.recordDimensions(image.width(), image.height());
            file.replaceVariants(variants);
            file.markClean();
            return files.save(file);
        });
    }

    /** Lee la maestra guardada (ya girada y sin metadatos) para difuminarla. */
    public ImageProcessor.Decoded load(StoredFile file) {
        try (InputStream in = storage.open(file.getStorageKey())) {
            return ImageProcessor.decode(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String cleanName(String original, ImageProcessor.Format format) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        name = name.replaceAll("[^\\p{L}\\p{N} ._()-]", "").strip();
        if (name.isEmpty()) {
            name = "foto";
        }
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        return name + "." + format.extension();
    }
}
