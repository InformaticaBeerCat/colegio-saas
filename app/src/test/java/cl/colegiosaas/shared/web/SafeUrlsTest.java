package cl.colegiosaas.shared.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeUrlsTest {

    @Test
    void acceptsSitePathsAndKnownSchemes() {
        assertThat(SafeUrls.isAllowed("/admision")).isTrue();
        assertThat(SafeUrls.isAllowed("/")).isTrue();
        assertThat(SafeUrls.isAllowed("#contacto")).isTrue();
        assertThat(SafeUrls.isAllowed("https://classroom.google.com")).isTrue();
        assertThat(SafeUrls.isAllowed("mailto:contacto@colegio.cl")).isTrue();
        assertThat(SafeUrls.isAllowed("tel:+56223456789")).isTrue();
    }

    @Test
    void rejectsScriptsAndProtocolRelativeUrls() {
        assertThat(SafeUrls.isAllowed("javascript:alert(1)")).isFalse();
        assertThat(SafeUrls.isAllowed("JavaScript:alert(1)")).isFalse();
        assertThat(SafeUrls.isAllowed("data:text/html,<script>")).isFalse();
        assertThat(SafeUrls.isAllowed("//evil.example")).isFalse();
        assertThat(SafeUrls.isAllowed("admision")).isFalse();
        assertThat(SafeUrls.isAllowed(null)).isFalse();
    }
}
