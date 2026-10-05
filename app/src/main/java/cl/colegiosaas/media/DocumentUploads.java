package cl.colegiosaas.media;

import cl.colegiosaas.shared.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;

/**
 * Subida de PDF (documentos institucionales, circulares, listas de útiles). El tipo se decide por la
 * firma del archivo ({@code %PDF-}), no por la extensión ni por lo que declara el navegador.
 *
 * Después pasa por el antivirus (SEG-03); un archivo con amenazas no se guarda.
 */
@Service
public class DocumentUploads {

    public static final long MAX_PDF_BYTES = 20L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final FileScanner scanner;
    private final Clock clock;

    DocumentUploads(StoredFileRepository files, FileStorage storage, FileScanner scanner, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.scanner = scanner;
        this.clock = clock;
    }

    @Transactional
    public StoredFile storePdf(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new FileUploadException("Adjunta el archivo PDF");
        }
        try {
            return storePdf(upload.getOriginalFilename(), upload.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Si el mismo PDF ya se subió, se reutiliza: una circular reenviada no duplica el archivo. */
    @Transactional
    public StoredFile storePdf(String originalName, byte[] content) {
        if (content.length == 0) {
            throw new FileUploadException("El archivo está vacío");
        }
        if (content.length > MAX_PDF_BYTES) {
            throw new FileUploadException("El PDF supera los 20 MB; comprímelo antes de subirlo");
        }
        if (content.length < PDF_SIGNATURE.length
                || !Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
            throw new FileUploadException("El archivo no es un PDF");
        }
        UploadChecks.requireClean(scanner, content);
        String sha256 = Hashes.sha256(content);
        return files.findFirstBySha256(sha256).orElseGet(() -> {
            YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
            String key = "documents/%d/%02d/%s.pdf".formatted(month.getYear(), month.getMonthValue(), sha256);
            storage.put(key, content);
            StoredFile file = new StoredFile(key, cleanName(originalName), "application/pdf", content.length, sha256);
            file.markClean();
            return files.save(file);
        });
    }

    /** Nombre de descarga: sin rutas ni caracteres raros, siempre terminado en .pdf. */
    static String cleanName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^\\p{L}\\p{N} ._()-]", "").strip();
        if (name.isEmpty() || name.startsWith(".")) {
            name = "documento.pdf";
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name.length() > 150 ? name.substring(0, 146) + ".pdf" : name;
    }
}
