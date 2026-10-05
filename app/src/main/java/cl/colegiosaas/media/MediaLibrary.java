package cl.colegiosaas.media;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Biblioteca de medios (MED-01): subida masiva con limpieza de metadatos y versiones optimizadas
 * (MED-02, MED-03, MED-10), videos de YouTube/Vimeo, difuminado (MED-07) y retiro (MED-09).
 * Toda foto nace pendiente de revisión: hasta que el gestor de consentimientos la apruebe no se ve
 * en ninguna parte del sitio (MED-06).
 */
@Service
public class MediaLibrary {

    public static final int MAX_FILES_PER_UPLOAD = 50;

    private final MediaAssetRepository assets;
    private final MediaFolderRepository folders;
    private final MediaTagRepository tags;
    private final UserAccountRepository users;
    private final ImageStore images;
    private final FileScanner scanner;
    private final List<PublicTextPolicy> textPolicies;
    private final AuditTrail audit;

    MediaLibrary(MediaAssetRepository assets, MediaFolderRepository folders, MediaTagRepository tags,
                 UserAccountRepository users, ImageStore images, FileScanner scanner,
                 List<PublicTextPolicy> textPolicies, AuditTrail audit) {
        this.assets = assets;
        this.folders = folders;
        this.tags = tags;
        this.users = users;
        this.images = images;
        this.scanner = scanner;
        this.textPolicies = textPolicies;
        this.audit = audit;
    }

    /** Archivo subido, ya leído (el controlador lo saca del multipart). */
    public record Upload(String name, byte[] content) {
    }

    /** Resultado por archivo: en una subida masiva, una foto mala no detiene a las demás. */
    public record UploadResult(String name, Long assetId, String problem) {
        public boolean ok() {
            return problem == null;
        }
    }

    // --- Consultas ---

    @Transactional(readOnly = true)
    public List<MediaAsset> list(ReviewStatus status, Long folderId, String tag) {
        return assets.findAllForLibrary().stream()
                .filter(a -> status == null || a.getReviewStatus() == status)
                .filter(a -> folderId == null || (a.getFolder() != null && a.getFolder().getId().equals(folderId)))
                .filter(a -> tag == null || tag.isBlank() || a.getTags().stream().anyMatch(t -> t.getName().equals(MediaTag.normalize(tag))))
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaAsset get(long id) {
        return assets.findWithDetailsById(id).orElseThrow(() -> new NotFound("El medio no existe"));
    }

    /** Imágenes que se pueden usar en el sitio (portadas, noticias, logo): aprobadas o exentas y no retiradas. */
    @Transactional(readOnly = true)
    public List<MediaAsset> displayableImages() {
        return assets.findAllForLibrary().stream()
                .filter(a -> a.getKind() == MediaKind.IMAGE && a.isDisplayable())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MediaFolder> folders() {
        return folders.findAll().stream().sorted(Comparator.comparing(MediaFolder::getName)).toList();
    }

    // --- Subida ---

    /**
     * Sube varias fotos (MED-03). Cada una se procesa y guarda por separado; las que fallan vuelven con
     * el motivo.
     */
    public List<UploadResult> uploadImages(List<Upload> uploads, long uploaderId, Long folderId, Set<String> tagNames) {
        if (uploads.size() > MAX_FILES_PER_UPLOAD) {
            throw new RuleViolation("Sube hasta " + MAX_FILES_PER_UPLOAD + " fotos por vez");
        }
        List<UploadResult> results = new ArrayList<>();
        for (Upload upload : uploads) {
            try {
                MediaAsset asset = uploadImage(upload, uploaderId, folderId, tagNames, false);
                results.add(new UploadResult(upload.name(), asset.getId(), null));
            } catch (FileUploadException | RuleViolation e) {
                results.add(new UploadResult(upload.name(), null, e.getMessage()));
            }
        }
        return results;
    }

    /**
     * Sube una foto. {@code exempt} la marca como "sin personas" desde el inicio: solo para logo y favicon,
     * que sube quien administra el diseño.
     */
    @Transactional
    public MediaAsset uploadImage(Upload upload, long uploaderId, Long folderId, Set<String> tagNames, boolean exempt) {
        UploadChecks.requireClean(scanner, upload.content());
        ImageProcessor.Decoded decoded = ImageProcessor.decode(upload.content());
        StoredFile file = images.store(ImageProcessor.process(decoded), upload.name());
        UserAccount uploader = user(uploaderId);
        MediaAsset asset = MediaAsset.upload(MediaKind.IMAGE, file, uploader);
        asset.setFolder(folder(folderId));
        applyTags(asset, tagNames);
        if (exempt) {
            asset.exemptFromReview(uploader);
        }
        assets.save(asset);
        audit.record(AuditAction.CREATE, "MediaAsset", asset.getId(), file.getOriginalName());
        return asset;
    }

    @Transactional
    public MediaAsset embedVideo(String url, String title, long uploaderId) {
        MediaAsset asset;
        try {
            asset = MediaAsset.embed(url == null ? null : url.strip(), user(uploaderId));
        } catch (IllegalArgumentException e) {
            throw new RuleViolation("Solo se aceptan videos de YouTube o Vimeo (https://…)");
        }
        asset.setCaption(blankToNull(title));
        assets.save(asset);
        audit.record(AuditAction.CREATE, "MediaAsset", asset.getId(), "Video " + asset.getEmbedUrl());
        return asset;
    }

    // --- Edición ---

    @Transactional
    public void update(long id, MediaDetails details) {
        MediaAsset asset = get(id);
        String alt = limit(details.altText(), 300, "El texto alternativo admite hasta 300 caracteres");
        String caption = limit(details.caption(), 500, "El pie de foto admite hasta 500 caracteres");
        checkPublicText(alt);
        checkPublicText(caption);
        asset.setAltText(alt);
        asset.setCaption(caption);
        asset.setCredits(limit(details.credits(), 255, "Los créditos admiten hasta 255 caracteres"));
        asset.setTakenOn(details.takenOn());
        asset.setFolder(folder(details.folderId()));
        Set<MediaTag> current = new HashSet<>(asset.getTags());
        current.forEach(asset::removeTag);
        applyTags(asset, details.tags());
        audit.record(AuditAction.UPDATE, "MediaAsset", id, alt);
    }

    /** Texto alternativo desde la revisión: el gestor lo completa si falta, para poder aprobar (ACC-02). */
    @Transactional
    public void updateAltText(long id, String altText) {
        MediaAsset asset = get(id);
        String alt = limit(altText, 300, "El texto alternativo admite hasta 300 caracteres");
        checkPublicText(alt);
        asset.setAltText(alt);
    }

    /**
     * Difumina zonas (MED-07). Siempre se parte de la foto original, así cambiar las zonas no acumula
     * difuminados; sin zonas, vuelve a publicarse la original. La original nunca se sirve si hay versión difuminada.
     */
    @Transactional
    public void blur(long id, List<BlurRegion> regions, long userId) {
        MediaAsset asset = get(id);
        if (asset.getKind() != MediaKind.IMAGE) {
            throw new RuleViolation("Solo se difuminan fotos");
        }
        if (regions.isEmpty()) {
            asset.applyBlur(List.of(), null);
        } else {
            ImageProcessor.Decoded original = images.load(asset.getFile());
            ImageProcessor.Decoded blurred = new ImageProcessor.Decoded(
                    ImageProcessor.blur(original.image(), regions), original.transparent());
            String name = asset.getFile().getOriginalName().replaceAll("\\.[a-z]+$", "") + "-difuminada";
            asset.applyBlur(regions, images.store(ImageProcessor.process(blurred), name));
        }
        audit.recordFor(user(userId), AuditAction.UPDATE, "MediaAsset", id, "Difuminado: " + regions.size() + " zona(s)");
    }

    /**
     * Saca el medio de todo el sitio de inmediato (MED-09): álbumes, noticias, portadas. Si la misma foto
     * se subió más de una vez (comparten archivo), se retiran todas las copias.
     */
    @Transactional
    public void withdraw(long id, String reason, long userId) {
        MediaAsset asset = get(id);
        List<MediaAsset> copies = asset.getFile() == null ? List.of(asset) : assets.findByFile(asset.getFile());
        for (MediaAsset copy : copies) {
            if (!copy.isWithdrawn()) {
                copy.withdraw(blankToNull(reason));
                audit.recordFor(user(userId), AuditAction.UNPUBLISH, "MediaAsset", copy.getId(),
                        "Retirada: " + (reason == null ? "" : reason));
            }
        }
    }

    /** Solo se borra lo que no se usa en ninguna parte; lo publicado se retira. */
    @Transactional
    public void delete(long id) {
        MediaAsset asset = get(id);
        if (assets.usageCount(asset) > 0) {
            throw new RuleViolation("El medio se usa en un álbum, noticia, taller o el diseño del sitio, o tiene estudiantes "
                    + "etiquetados; retíralo en vez de eliminarlo");
        }
        assets.delete(asset);
        audit.record(AuditAction.DELETE, "MediaAsset", id, null);
    }

    @Transactional
    public MediaFolder createFolder(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new RuleViolation("La carpeta necesita un nombre de hasta 100 caracteres");
        }
        return folders.save(new MediaFolder(name.strip(), null));
    }

    private void checkPublicText(String text) {
        if (text == null) {
            return;
        }
        for (PublicTextPolicy policy : textPolicies) {
            List<String> problems = policy.problems(text);
            if (!problems.isEmpty()) {
                throw new RuleViolation(String.join(" ", problems));
            }
        }
    }

    private void applyTags(MediaAsset asset, Set<String> names) {
        for (String raw : names) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = MediaTag.normalize(raw);
            if (name.length() > 60) {
                throw new RuleViolation("Cada etiqueta admite hasta 60 caracteres");
            }
            asset.addTag(tags.findByName(name).orElseGet(() -> tags.save(new MediaTag(name))));
        }
    }

    private MediaFolder folder(Long id) {
        return id == null ? null : folders.findById(id).orElseThrow(() -> new RuleViolation("La carpeta no existe"));
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow(() -> new NotFound("La cuenta no existe"));
    }

    private static String limit(String value, int max, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new RuleViolation(message);
        }
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
