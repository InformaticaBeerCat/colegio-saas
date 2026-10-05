package cl.colegiosaas.shared.html;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void keepsFormattingAndRemovesScriptsStylesAndImages() {
        String clean = HtmlSanitizer.richText("""
                <h2>Misión</h2><p style="color:red" onclick="alert(1)">Formar <strong>personas</strong></p>
                <script>alert('x')</script><img src="https://x.cl/foto.jpg"><iframe src="https://x.cl"></iframe>
                <ul><li>Uno</li></ul>""");

        assertThat(clean).contains("<h2>Misión</h2>", "<strong>personas</strong>", "<li>Uno</li>")
                .doesNotContain("script", "style=", "onclick", "<img", "iframe");
    }

    @Test
    void linksStayButCannotRunCodeAndDoNotLeakTheOpener() {
        String clean = HtmlSanitizer.richText(
                "<a href=\"/admision\">Admisión</a> <a href=\"https://mineduc.cl\" target=\"_blank\">Mineduc</a> <a href=\"javascript:alert(1)\">x</a>");

        assertThat(clean).contains("href=\"/admision\"", "href=\"https://mineduc.cl\"", "rel=\"noopener noreferrer\"")
                .doesNotContain("javascript");
    }

    @Test
    void emptyInputIsEmptyText() {
        assertThat(HtmlSanitizer.richText(null)).isEmpty();
        assertThat(HtmlSanitizer.richText("   ")).isEmpty();
    }
}
