package cl.colegiosaas.media;

import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.Course;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Galería de fotos de un evento o actividad. El álbum es el dueño de sus ítems:
 * agregarlos, quitarlos y ordenarlos pasa por aquí (Hibernate los guarda en cascada).
 *
 * Un álbum no se publica mientras tenga fotos pendientes de revisión (MED-06).
 * Una foto agregada a un álbum ya publicado queda oculta hasta que la aprueben.
 */
@Entity
@Table(name = "album")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Album extends BaseEntity {

    @NotBlank
    @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
    private String slug;

    @NotBlank
    private String title;

    private String description;

    private LocalDate takenOn;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private AlbumVisibility visibility = AlbumVisibility.PUBLIC;

    /** Solo con visibilidad COURSE (la tabla lo exige con un CHECK). */
    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private AlbumStatus status = AlbumStatus.DRAFT;

    @Setter(AccessLevel.NONE)
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private MediaAsset cover;

    /** MED-11 (v2): por defecto no se ofrece descarga. */
    private boolean downloadAllowed;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<AlbumItem> items = new ArrayList<>();

    public Album(String slug, String title) {
        this.slug = slug;
        this.title = title;
    }

    public void makePublic() {
        visibility = AlbumVisibility.PUBLIC;
        course = null;
    }

    public void restrictToCommunity() {
        visibility = AlbumVisibility.COMMUNITY;
        course = null;
    }

    public void restrictToCourse(Course newCourse) {
        visibility = AlbumVisibility.COURSE;
        course = Objects.requireNonNull(newCourse, "course");
    }

    // --- Ítems ---

    public void addItem(MediaAsset asset) {
        if (contains(asset)) {
            return;
        }
        int next = items.stream().mapToInt(AlbumItem::getSortOrder).max().orElse(-1) + 1;
        items.add(new AlbumItem(this, asset, next));
    }

    public void removeItem(MediaAsset asset) {
        items.removeIf(item -> item.getAsset().equals(asset));
        if (asset.equals(cover)) {
            cover = null;
        }
    }

    /** Reordena según la lista recibida (p. ej., después de arrastrar fotos en el editor). */
    public void reorder(List<MediaAsset> order) {
        for (AlbumItem item : items) {
            int index = order.indexOf(item.getAsset());
            item.moveTo(index >= 0 ? index : Integer.MAX_VALUE);
        }
        items.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
    }

    public boolean contains(MediaAsset asset) {
        return items.stream().anyMatch(item -> item.getAsset().equals(asset));
    }

    public List<MediaAsset> getAssets() {
        return items.stream().map(AlbumItem::getAsset).toList();
    }

    /** Lo que ven los visitantes: sin fotos pendientes, rechazadas ni retiradas. */
    public List<MediaAsset> visibleAssets() {
        return items.stream().map(AlbumItem::getAsset).filter(MediaAsset::isDisplayable).toList();
    }

    public long pendingReviewCount() {
        return items.stream().filter(item -> item.getAsset().getReviewStatus() == ReviewStatus.PENDING_REVIEW).count();
    }

    public Optional<MediaAsset> coverOrFirstVisible() {
        if (cover != null && cover.isDisplayable()) {
            return Optional.of(cover);
        }
        return visibleAssets().stream().findFirst();
    }

    // --- Publicación ---

    public void publish() {
        if (pendingReviewCount() > 0) {
            throw new IllegalStateException("El álbum tiene fotos pendientes de revisión de autorización de imagen");
        }
        if (visibleAssets().isEmpty()) {
            throw new IllegalStateException("El álbum no tiene fotos aprobadas");
        }
        status = AlbumStatus.PUBLISHED;
        publishedAt = Instant.now();
    }

    public void unpublish() {
        status = AlbumStatus.DRAFT;
    }

    public boolean isIndexable() {
        return visibility == AlbumVisibility.PUBLIC;
    }
}
