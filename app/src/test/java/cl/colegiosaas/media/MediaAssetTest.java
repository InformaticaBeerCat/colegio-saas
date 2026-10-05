package cl.colegiosaas.media;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaAssetTest {

    final UserAccount editor = new UserAccount("editor@colegio.cl", "Editora", Role.EDITOR);
    final UserAccount reviewer = new UserAccount("consentimientos@colegio.cl", "Gestor", Role.CONSENT_MANAGER);

    @Test
    void newPhotosStayHiddenUntilReviewed() {
        MediaAsset photo = MediaAsset.upload(MediaKind.IMAGE, file("foto.jpg"), editor);
        assertThat(photo.getReviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(photo.isDisplayable()).isFalse();

        photo.setAltText("Estudiantes en la feria científica");
        photo.approve(reviewer);
        assertThat(photo.isDisplayable()).isTrue();
        assertThat(photo.getReviewedBy()).isSameAs(reviewer);
    }

    @Test
    void documentsDoNotNeedImageReview() {
        MediaAsset pdf = MediaAsset.upload(MediaKind.DOCUMENT, file("lista-utiles.pdf"), editor);

        assertThat(pdf.getReviewStatus()).isEqualTo(ReviewStatus.NOT_REQUIRED);
        assertThat(pdf.isDisplayable()).isTrue();
    }

    @Test
    void imagesNeedAltTextToBeApproved() {
        MediaAsset photo = MediaAsset.upload(MediaKind.IMAGE, file("foto.jpg"), editor);

        assertThatThrownBy(() -> photo.approve(reviewer)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedPhotosStayHidden() {
        MediaAsset photo = MediaAsset.upload(MediaKind.IMAGE, file("foto.jpg"), editor);
        photo.reject(reviewer, "Aparece un estudiante sin autorización para web");

        assertThat(photo.isDisplayable()).isFalse();
        assertThat(photo.getReviewNote()).contains("sin autorización");
    }

    @Test
    void withdrawalHidesEvenAnApprovedPhoto() {
        MediaAsset photo = approvedPhoto();

        photo.withdraw("El apoderado revocó la autorización");

        assertThat(photo.isDisplayable()).isFalse();
        assertThat(photo.isWithdrawn()).isTrue();
    }

    @Test
    void visitorsGetTheBlurredVersionWhenThereIsOne() {
        MediaAsset photo = approvedPhoto();
        StoredFile blurred = file("foto-difuminada.jpg");

        photo.applyBlur(List.of(new BlurRegion(0.1, 0.2, 0.15, 0.15)), blurred);
        assertThat(photo.publicFile()).isSameAs(blurred);

        photo.applyBlur(List.of(), null);
        assertThat(photo.publicFile()).isSameAs(photo.getFile());
    }

    @Test
    void blurRegionMustFitInsideTheImage() {
        assertThatThrownBy(() -> new BlurRegion(0.9, 0.5, 0.2, 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlurRegion(-0.1, 0, 0.2, 0.2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyYouTubeAndVimeoCanBeEmbedded() {
        assertThat(MediaAsset.embed("https://www.youtube.com/watch?v=abc", editor).getKind())
                .isEqualTo(MediaKind.EMBEDDED_VIDEO);
        assertThatThrownBy(() -> MediaAsset.embed("https://sitio-raro.com/video", editor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaAsset.embed("http://www.youtube.com/watch?v=abc", editor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MediaAsset approvedPhoto() {
        MediaAsset photo = MediaAsset.upload(MediaKind.IMAGE, file("foto.jpg"), editor);
        photo.setAltText("Acto de fiestas patrias");
        photo.approve(reviewer);
        return photo;
    }

    static StoredFile file(String name) {
        return new StoredFile("media/" + name, name, "image/jpeg", 1024, "a".repeat(64));
    }
}
