package cl.colegiosaas.media;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cl.colegiosaas.media.MediaAssetTest.file;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlbumTest {

    final UserAccount editor = new UserAccount("editor@colegio.cl", "Editora", Role.EDITOR);
    final UserAccount reviewer = new UserAccount("consentimientos@colegio.cl", "Gestor", Role.CONSENT_MANAGER);

    @Test
    void albumCannotBePublishedWhilePhotosArePendingReview() {
        Album album = new Album("feria-ciencias-2026", "Feria de Ciencias 2026");
        MediaAsset first = photo();
        MediaAsset second = photo();
        album.addItem(first);
        album.addItem(second);

        assertThat(album.pendingReviewCount()).isEqualTo(2);
        assertThatThrownBy(album::publish).isInstanceOf(IllegalStateException.class);

        approve(first);
        second.reject(reviewer, "Sin autorización");
        album.publish();

        assertThat(album.getStatus()).isEqualTo(AlbumStatus.PUBLISHED);
        assertThat(album.visibleAssets()).containsExactly(first);
    }

    @Test
    void photoAddedToAPublishedAlbumStaysHiddenUntilApproved() {
        Album album = publishedAlbumWith(photo());
        MediaAsset late = photo();

        album.addItem(late);

        assertThat(album.getStatus()).isEqualTo(AlbumStatus.PUBLISHED);
        assertThat(album.visibleAssets()).doesNotContain(late);
    }

    @Test
    void withdrawnCoverFallsBackToFirstVisiblePhoto() {
        MediaAsset cover = photo();
        MediaAsset other = photo();
        Album album = publishedAlbumWith(cover, other);
        album.setCover(cover);

        cover.withdraw("Revocación");

        assertThat(album.coverOrFirstVisible()).contains(other);
    }

    @Test
    void sameAssetIsAddedOnlyOnceAndCanBeReordered() {
        Album album = new Album("acto", "Acto");
        MediaAsset a = photo();
        MediaAsset b = photo();
        MediaAsset c = photo();
        album.addItem(a);
        album.addItem(b);
        album.addItem(a);
        album.addItem(c);

        album.reorder(List.of(c, a, b));

        assertThat(album.getAssets()).containsExactly(c, a, b);
    }

    @Test
    void removingTheCoverClearsIt() {
        MediaAsset cover = photo();
        Album album = publishedAlbumWith(cover, photo());
        album.setCover(cover);

        album.removeItem(cover);

        assertThat(album.getCover()).isNull();
        assertThat(album.contains(cover)).isFalse();
    }

    private Album publishedAlbumWith(MediaAsset... assets) {
        Album album = new Album("album", "Álbum");
        for (MediaAsset asset : assets) {
            approve(asset);
            album.addItem(asset);
        }
        album.publish();
        return album;
    }

    private MediaAsset photo() {
        return MediaAsset.upload(MediaKind.IMAGE, file("foto.jpg"), editor);
    }

    private void approve(MediaAsset asset) {
        asset.setAltText("Foto del evento");
        asset.approve(reviewer);
    }
}
