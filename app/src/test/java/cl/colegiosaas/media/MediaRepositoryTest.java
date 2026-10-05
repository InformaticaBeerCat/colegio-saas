package cl.colegiosaas.media;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MediaRepositoryTest {

    @Autowired
    MediaAssetRepository assets;

    @Autowired
    AlbumRepository albums;

    @Autowired
    MediaTagRepository tags;

    @Autowired
    StoredFileRepository files;

    @Autowired
    UserAccountRepository users;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    UserAccount editor;
    UserAccount reviewer;
    int fileCounter;

    @BeforeEach
    void setUp() {
        editor = users.save(new UserAccount("editor@colegio.cl", "Editora", Role.EDITOR));
        reviewer = users.save(new UserAccount("consentimientos@colegio.cl", "Gestor", Role.CONSENT_MANAGER));
    }

    @Test
    void albumItemsAreSavedInCascadeAndLoadedInOrder() {
        MediaAsset first = assets.save(approvedPhoto());
        MediaAsset second = assets.save(approvedPhoto());
        Album album = new Album("aniversario", "Aniversario 40 años");
        album.addItem(second);
        album.addItem(first);
        album.publish();
        albums.saveAndFlush(album);
        em.clear();

        Album loaded = albums.findBySlug("aniversario").orElseThrow();
        assertThat(loaded.getAssets()).extracting(MediaAsset::getId).containsExactly(second.getId(), first.getId());
        assertThat(loaded.getStatus()).isEqualTo(AlbumStatus.PUBLISHED);
    }

    @Test
    void removingAnItemDeletesItsRow() {
        MediaAsset photo = assets.save(approvedPhoto());
        Album album = new Album("acto", "Acto");
        album.addItem(photo);
        albums.saveAndFlush(album);
        em.clear();

        Album loaded = albums.findBySlug("acto").orElseThrow();
        loaded.removeItem(em.find(MediaAsset.class, photo.getId()));
        em.flush();

        assertThat(jdbc.queryForObject("select count(*) from album_item", Integer.class)).isZero();
    }

    @Test
    void reviewQueueAndTagsAreQueryable() {
        MediaTag fair = tags.save(new MediaTag("Feria Ciencias"));
        MediaAsset pending = MediaAsset.upload(MediaKind.IMAGE, storedFile(), editor);
        pending.addTag(fair);
        assets.save(pending);
        assets.save(approvedPhoto());
        em.flush();
        em.clear();

        assertThat(assets.findByReviewStatusOrderByCreatedAtAsc(ReviewStatus.PENDING_REVIEW))
                .extracting(MediaAsset::getId).containsExactly(pending.getId());
        assertThat(assets.findByTags_NameOrderByCreatedAtDesc("feria ciencias"))
                .extracting(MediaAsset::getId).containsExactly(pending.getId());
    }

    @Test
    void blurRegionsSurviveAJsonRoundTrip() {
        MediaAsset photo = approvedPhoto();
        photo.applyBlur(List.of(new BlurRegion(0.1, 0.1, 0.2, 0.25)), storedFile());
        assets.saveAndFlush(photo);
        em.clear();

        MediaAsset loaded = assets.findById(photo.getId()).orElseThrow();
        assertThat(loaded.getBlurRegions()).containsExactly(new BlurRegion(0.1, 0.1, 0.2, 0.25));
        assertThat(loaded.publicFile().getId()).isEqualTo(loaded.getBlurredFile().getId());
    }

    @Test
    void databaseRequiresACourseOnlyForCourseAlbums() {
        assertThatThrownBy(() -> insertAlbum("'COURSE'", "null")).isInstanceOf(DataIntegrityViolationException.class);
        insertAlbum("'PUBLIC'", "null");
    }

    @Test
    void databaseRejectsAnEmbeddedVideoWithAFile() {
        StoredFile file = files.saveAndFlush(storedFile());

        assertThatThrownBy(() -> jdbc.update("""
                insert into media_asset (version, created_at, updated_at, kind, file_id, embed_url, review_status, blur_regions)
                values (0, current_timestamp, current_timestamp, 'EMBEDDED_VIDEO', ?, 'https://youtu.be/x', 'PENDING_REVIEW', '[]')
                """, file.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertAlbum(String visibility, String courseId) {
        jdbc.update("""
                insert into album (version, created_at, updated_at, slug, title, visibility, course_id, status, download_allowed)
                values (0, current_timestamp, current_timestamp, 'album-%d', 'X', %s, %s, 'DRAFT', false)
                """.formatted(++fileCounter, visibility, courseId));
    }

    private MediaAsset approvedPhoto() {
        MediaAsset photo = MediaAsset.upload(MediaKind.IMAGE, storedFile(), editor);
        photo.setAltText("Foto del evento");
        photo.approve(reviewer);
        return photo;
    }

    private StoredFile storedFile() {
        int n = ++fileCounter;
        return files.save(new StoredFile("media/foto-" + n + ".jpg", "foto.jpg", "image/jpeg", 2048, "%064d".formatted(n)));
    }
}
