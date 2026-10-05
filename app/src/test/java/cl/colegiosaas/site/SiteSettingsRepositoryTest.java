package cl.colegiosaas.site;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SiteSettingsRepositoryTest {

    @Autowired
    SiteSettingsRepository repository;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

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
        assertThat(json).contains("\"primary\":\"#1F3A5F\"").contains("\"theme\":\"base\"");
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
    void hexColorIsValidatedAndNormalized() {
        assertThat(HexColor.of("#abcdef").value()).isEqualTo("#ABCDEF");
        assertThatThrownBy(() -> HexColor.of("red")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HexColor.of("#FFF")).isInstanceOf(IllegalArgumentException.class);
    }
}
