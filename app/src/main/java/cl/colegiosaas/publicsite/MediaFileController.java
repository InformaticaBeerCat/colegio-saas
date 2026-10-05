package cl.colegiosaas.publicsite;

import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaAssetRepository;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.media.StoredFileRepository;
import cl.colegiosaas.shared.storage.FileStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Entrega de fotos. En el sitio ({@code /medios/…}) solo las aprobadas o exentas y no retiradas, y siempre
 * la versión difuminada si existe. En el panel ({@code /admin/media/files/…}) también las pendientes, para
 * revisarlas. El caché del público es de una hora: una foto retirada deja de verse pronto aunque alguien la
 * tenga en caché.
 */
@RestController
class MediaFileController {

    private final StoredFileRepository files;
    private final MediaAssetRepository assets;
    private final FileStorage storage;

    MediaFileController(StoredFileRepository files, MediaAssetRepository assets, FileStorage storage) {
        this.files = files;
        this.assets = assets;
        this.storage = storage;
    }

    @GetMapping("/medios/{sha256:[0-9a-f]{64}}/{name:[a-z0-9]+\\.[a-z]+}")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> publicImage(@PathVariable String sha256, @PathVariable String name) {
        StoredFile file = files.findFirstBySha256(sha256).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isPublic(file, assets.findByPublicFile(file))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return serve(file, name, CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
    }

    @GetMapping("/admin/media/files/{sha256:[0-9a-f]{64}}/{name:[a-z0-9]+\\.[a-z]+}")
    @PreAuthorize("hasAnyAuthority('MEDIA_UPLOAD', 'MEDIA_REVIEW', 'SITE_DESIGN', 'PAGES', 'NEWS_EDIT')")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> preview(@PathVariable String sha256, @PathVariable String name) {
        StoredFile file = files.findFirstBySha256(sha256)
                .filter(f -> f.getContentType().startsWith("image/"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return serve(file, name, CacheControl.noStore());
    }

    /**
     * Se entrega si algún medio aprobado la publica tal cual (la difuminada, o la original si no hay
     * difuminado) y ningún medio que la usa fue retirado: el retiro siempre gana.
     */
    static boolean isPublic(StoredFile file, List<MediaAsset> owners) {
        if (owners.stream().anyMatch(MediaAsset::isWithdrawn)) {
            return false;
        }
        return owners.stream().anyMatch(a -> a.isDisplayable() && a.publicFile().equals(file));
    }

    /** {@code original.jpg} es la maestra; {@code 960.webp} la variante de ese ancho y formato. */
    private ResponseEntity<InputStreamResource> serve(StoredFile file, String name, CacheControl cache) {
        if (!file.isServable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String[] parts = name.split("\\.");
        String key;
        long size;
        MediaType type;
        if (parts[0].equals("original")) {
            key = file.getStorageKey();
            size = file.getSizeBytes();
            type = MediaType.parseMediaType(file.getContentType());
        } else {
            Optional<StoredFile.ImageVariant> variant = file.getVariants().stream()
                    .filter(v -> String.valueOf(v.width()).equals(parts[0]) && v.format().equals(parts[1]))
                    .findFirst();
            StoredFile.ImageVariant v = variant.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            key = v.storageKey();
            size = v.sizeBytes();
            type = MediaType.parseMediaType(switch (v.format()) {
                case "webp" -> "image/webp";
                case "png" -> "image/png";
                default -> "image/jpeg";
            });
        }
        return ResponseEntity.ok().contentType(type).contentLength(size).cacheControl(cache)
                .body(new InputStreamResource(storage.open(key)));
    }
}
