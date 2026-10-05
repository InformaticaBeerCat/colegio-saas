package cl.colegiosaas.media;

import java.io.UncheckedIOException;

/** Revisión común a toda subida: el antivirus (SEG-03). */
final class UploadChecks {

    private UploadChecks() {
    }

    static void requireClean(FileScanner scanner, byte[] content) {
        FileScanner.Verdict verdict;
        try {
            verdict = scanner.scan(content);
        } catch (UncheckedIOException e) {
            throw new FileUploadException(e.getMessage());
        }
        if (!verdict.clean()) {
            throw new FileUploadException("El antivirus detectó una amenaza en el archivo (" + verdict.signature() + "); no se guardó");
        }
    }
}
