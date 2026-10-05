package cl.colegiosaas.media;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.text.Slugs;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Álbumes de fotos (MED-05). Un álbum no se publica con fotos pendientes de revisión (MED-06); una foto
 * agregada después queda oculta hasta que la aprueben. Los álbumes de comunidad o de curso no aparecen
 * en el sitio público (se ven en la zona comunidad, v2).
 */
@Service
public class AlbumService {

    private final AlbumRepository albums;
    private final MediaAssetRepository assets;
    private final CourseRepository courses;
    private final AuditTrail audit;

    AlbumService(AlbumRepository albums, MediaAssetRepository assets, CourseRepository courses, AuditTrail audit) {
        this.albums = albums;
        this.assets = assets;
        this.courses = courses;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Album> list() {
        return albums.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Album get(long id) {
        return albums.findWithItemsById(id).orElseThrow(() -> new NotFound("El álbum no existe"));
    }

    @Transactional
    public Album create(String title, LocalDate takenOn) {
        requireTitle(title);
        String base = title + (takenOn == null ? "" : " " + takenOn.getYear());
        Album album = new Album(Slugs.unique(base, "album", albums::existsBySlug), title.strip());
        album.setTakenOn(takenOn);
        albums.save(album);
        audit.record(AuditAction.CREATE, "Album", album.getId(), album.getTitle());
        return album;
    }

    @Transactional
    public void update(long id, String title, String description, LocalDate takenOn, AlbumVisibility visibility, Long courseId) {
        Album album = get(id);
        requireTitle(title);
        if (description != null && description.length() > 1000) {
            throw new RuleViolation("La descripción admite hasta 1000 caracteres");
        }
        album.setTitle(title.strip());
        album.setDescription(description == null || description.isBlank() ? null : description.strip());
        album.setTakenOn(takenOn);
        switch (visibility == null ? AlbumVisibility.PUBLIC : visibility) {
            case PUBLIC -> album.makePublic();
            case COMMUNITY -> album.restrictToCommunity();
            case COURSE -> {
                if (courseId == null) {
                    throw new RuleViolation("Elige el curso que podrá ver el álbum");
                }
                album.restrictToCourse(courses.findById(courseId).orElseThrow(() -> new RuleViolation("El curso no existe")));
            }
        }
        audit.record(AuditAction.UPDATE, "Album", id, album.getTitle());
    }

    /** Agrega fotos y videos de la biblioteca (los ya incluidos se ignoran). Los retirados no se agregan. */
    @Transactional
    public int addAssets(long id, Collection<Long> assetIds) {
        Album album = get(id);
        int added = 0;
        for (MediaAsset asset : assets.findAllById(assetIds)) {
            if (asset.getKind() == MediaKind.DOCUMENT || asset.isWithdrawn() || album.contains(asset)) {
                continue;
            }
            album.addItem(asset);
            added++;
        }
        return added;
    }

    @Transactional
    public void removeAsset(long id, long assetId) {
        Album album = get(id);
        assets.findById(assetId).ifPresent(album::removeItem);
    }

    /** Mueve una foto una posición antes (-1) o después (+1). */
    @Transactional
    public void move(long id, long assetId, int direction) {
        Album album = get(id);
        List<MediaAsset> order = new ArrayList<>(album.getAssets());
        int index = indexOf(order, assetId);
        int target = index + Integer.signum(direction);
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        order.add(target, order.remove(index));
        album.reorder(order);
    }

    @Transactional
    public void setCover(long id, long assetId) {
        Album album = get(id);
        MediaAsset asset = album.getAssets().stream().filter(a -> a.getId() == assetId).findFirst()
                .orElseThrow(() -> new RuleViolation("La portada tiene que ser una foto del álbum"));
        album.setCover(asset);
    }

    @Transactional
    public void publish(long id) {
        Album album = get(id);
        try {
            album.publish();
        } catch (IllegalStateException e) {
            throw new RuleViolation(e.getMessage());
        }
        audit.record(AuditAction.PUBLISH, "Album", id, album.getTitle());
    }

    @Transactional
    public void unpublish(long id) {
        Album album = get(id);
        album.unpublish();
        audit.record(AuditAction.UNPUBLISH, "Album", id, album.getTitle());
    }

    @Transactional
    public void delete(long id) {
        Album album = get(id);
        if (album.getStatus() == AlbumStatus.PUBLISHED) {
            throw new RuleViolation("Despublica el álbum antes de eliminarlo");
        }
        albums.delete(album);
        audit.record(AuditAction.DELETE, "Album", id, album.getTitle());
    }

    // --- Sitio público ---

    /** Álbumes publicados y públicos con al menos una foto visible, los más recientes primero. */
    @Transactional(readOnly = true)
    public List<Album> publicAlbums() {
        return albums.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> a.getStatus() == AlbumStatus.PUBLISHED && a.getVisibility() == AlbumVisibility.PUBLIC)
                .filter(a -> !a.visibleAssets().isEmpty())
                .sorted(Comparator.comparing((Album a) -> a.getTakenOn() == null ? LocalDate.MIN : a.getTakenOn()).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public Album publicAlbum(String slug) {
        return albums.findBySlug(slug)
                .filter(a -> a.getStatus() == AlbumStatus.PUBLISHED && a.getVisibility() == AlbumVisibility.PUBLIC)
                .orElseThrow(() -> new NotFound("El álbum no existe"));
    }

    /** Para el bloque galería: el álbum por id si es público y está publicado. */
    @Transactional(readOnly = true)
    public Album publicAlbum(long id) {
        return albums.findWithItemsById(id)
                .filter(a -> a.getStatus() == AlbumStatus.PUBLISHED && a.getVisibility() == AlbumVisibility.PUBLIC)
                .orElse(null);
    }

    private static int indexOf(List<MediaAsset> list, long assetId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == assetId) {
                return i;
            }
        }
        return -1;
    }

    private static void requireTitle(String title) {
        if (title == null || title.isBlank() || title.strip().length() > 200) {
            throw new RuleViolation("El álbum necesita un título de hasta 200 caracteres");
        }
    }
}
