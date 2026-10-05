package cl.colegiosaas.publicsite;

import cl.colegiosaas.documents.InstitutionalDocumentRepository;
import cl.colegiosaas.info.InfoSheetRepository;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.media.StoredFileRepository;
import cl.colegiosaas.news.AnnouncementRepository;
import cl.colegiosaas.shared.storage.FileStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Descarga de PDF públicos ({@code /archivos/<sha256>/<nombre>}). Solo entrega archivos que pertenecen
 * a contenido publicado: versiones de documentos institucionales, circulares y fichas de información.
 * Cualquier otro archivo (evidencia de autorizaciones, borradores) responde 404 aunque se conozca su huella.
 */
@RestController
class PublicFileController {

    private final StoredFileRepository files;
    private final InstitutionalDocumentRepository documents;
    private final AnnouncementRepository announcements;
    private final InfoSheetRepository sheets;
    private final FileStorage storage;

    PublicFileController(StoredFileRepository files, InstitutionalDocumentRepository documents,
                         AnnouncementRepository announcements, InfoSheetRepository sheets, FileStorage storage) {
        this.files = files;
        this.documents = documents;
        this.announcements = announcements;
        this.sheets = sheets;
        this.storage = storage;
    }

    /** El nombre en la URL es solo para que la descarga se guarde con un nombre legible; no se usa. */
    @GetMapping("/archivos/{sha256:[0-9a-f]{64}}/{name}")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(@PathVariable String sha256, @PathVariable String name) {
        StoredFile file = files.findFirstBySha256(sha256)
                .filter(StoredFile::isServable)
                .filter(this::isPublic)
                .filter(f -> storage.exists(f.getStorageKey()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(storage.open(file.getStorageKey())));
    }

    private boolean isPublic(StoredFile file) {
        return documents.isPublishedFile(file)
                || announcements.existsByAttachmentAndPublishedAtIsNotNullAndCommunityOnlyFalse(file)
                || sheets.existsByFileAndPublishedTrue(file);
    }
}
