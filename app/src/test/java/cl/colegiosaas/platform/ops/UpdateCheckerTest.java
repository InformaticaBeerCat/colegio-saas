package cl.colegiosaas.platform.ops;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateCheckerTest {

    @Test
    void readsGithubReleasesAndAnOwnFeed() {
        UpdateChecker.Release github = UpdateChecker.parse("""
                {"tag_name": "v1.4.0", "html_url": "https://github.com/x/y/releases/v1.4.0", "body": "Mejoras"}""");
        assertThat(github.version()).isEqualTo("1.4.0");
        assertThat(github.url()).endsWith("v1.4.0");
        assertThat(UpdateChecker.parse("{\"version\": \"2.0.1\", \"notes\": \"x\"}").version()).isEqualTo("2.0.1");
        assertThatThrownBy(() -> UpdateChecker.parse("{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UpdateChecker.parse("<html>")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesVersionsNumerically() {
        assertThat(UpdateChecker.isNewer("1.10.0", "1.9.3")).isTrue();
        assertThat(UpdateChecker.isNewer("1.2.0", "1.2.0")).isFalse();
        assertThat(UpdateChecker.isNewer("1.2.0", "1.3.0")).isFalse();
        assertThat(UpdateChecker.isNewer("1.2.0", "1.2.0-SNAPSHOT")).isTrue();
        assertThat(UpdateChecker.isNewer("1.2", "1.2.0")).isFalse();
    }
}
