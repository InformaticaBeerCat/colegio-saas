package cl.colegiosaas.media;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Foto o video dentro de un álbum. Solo se crea y ordena a través de {@link Album}. */
@Entity
@Table(name = "album_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlbumItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private MediaAsset asset;

    private int sortOrder;

    AlbumItem(Album album, MediaAsset asset, int sortOrder) {
        this.album = album;
        this.asset = asset;
        this.sortOrder = sortOrder;
    }

    void moveTo(int newSortOrder) {
        sortOrder = newSortOrder;
    }
}
