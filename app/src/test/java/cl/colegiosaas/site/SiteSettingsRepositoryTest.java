package cl.colegiosaas.site;

import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaAssetRepository;
import cl.colegiosaas.media.MediaKind;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.media.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class SiteSettingsRepositoryTest {

    @Autowired
    SiteSettingsRepository repository;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StoredFileRepository files;

    @Autowired
    MediaAssetRepository assets;

    @Test
    void designAndSocialLinksSurviveAJsonRoundTrip() {
        SiteSettings settings = new SiteSettings(SiteDesign.defaults());
        settings.replaceSocialLinks(List.of(
                new SocialLink(SocialLink.Network.INSTAGRAM, "https://instagram.com/colegio")));
        repository.saveAndFlush(settings);
        em.clear();

        SiteSettings loaded = repository.findSingleton().orElseThrow();
        assertThat(loaded.getPublishedDesign()).isEqualTo(SiteDesign.defaults());
        assertThat(loaded.getSocialLinks()).extracting(SocialLink::network).containsExactly(SocialLink.Network.INSTAGRAM);
        assertThat(loaded.hasUnpublishedChanges()).isFalse();
    }

    @Test
    void colorsAreStoredAsPlainHexStrings() {
        repository.saveAndFlush(new SiteSettings(SiteDesign.defaults()));

        String json = jdbc.queryForObject("select published_design from site_settings", String.class);
        assertThat(json).contains("\"primary\":\"#1F3A5F\"").contains("\"theme\":\"classic\"");
    }

    @Test
    void draftChangesStayHiddenUntilPublished() {
        repository.saveAndFlush(new SiteSettings(SiteDesign.defaults()));
        em.clear();

        // Entidad cargada: se modifica sin save(); el flush la guarda.
        SiteSettings settings = repository.findSingleton().orElseThrow();
        SiteDesign.Palette newPalette = new SiteDesign.Palette(
                HexColor.of("#7a1f2b"), HexColor.of("#1F3A5F"), HexColor.of("#E0A526"),
                HexColor.of("#FFFFFF"), HexColor.of("#F4F5F7"), HexColor.of("#1A1A1A"));
        settings.updateDraft(settings.getDraftDesign().withPalette(newPalette));
        em.flush();
        em.clear();

        SiteSettings reloaded = repository.findSingleton().orElseThrow();
        assertThat(reloaded.hasUnpublishedChanges()).isTrue();
        assertThat(reloaded.getPublishedDesign().palette().primary()).isEqualTo(HexColor.of("#1F3A5F"));

        reloaded.publishDraft();
        em.flush();
        em.clear();

        assertThat(repository.findSingleton().orElseThrow().getPublishedDesign().palette().primary())
                .isEqualTo(HexColor.of("#7A1F2B"));
    }

    @Test
    void logoIsAMediaAssetOfTheLibrary() {
        StoredFile file = files.save(new StoredFile("brand/logo.svg", "logo.svg", "image/svg+xml", 512, "b".repeat(64)));
        MediaAsset logo = assets.save(MediaAsset.upload(MediaKind.IMAGE, file, null));
        SiteSettings settings = new SiteSettings(SiteDesign.defaults());
        settings.setLogo(logo);
        repository.saveAndFlush(settings);
        em.clear();

        assertThat(repository.findSingleton().orElseThrow().getLogo().getId()).isEqualTo(logo.getId());
    }

    @Test
    void hexColorIsValidatedAndNormalized() {
        assertThat(HexColor.of("#abcdef").value()).isEqualTo("#ABCDEF");
        assertThatThrownBy(() -> HexColor.of("red")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HexColor.of("#FFF")).isInstanceOf(IllegalArgumentException.class);
    }
}
