package cl.colegiosaas.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentUploadsTest {

    @Test
    void downloadNamesAreCleanedAndAlwaysEndInPdf() {
        assertThat(DocumentUploads.cleanName("C:\\Users\\ana\\Reglamento Interno 2026.pdf")).isEqualTo("Reglamento Interno 2026.pdf");
        assertThat(DocumentUploads.cleanName("../../etc/passwd")).isEqualTo("passwd.pdf");
        assertThat(DocumentUploads.cleanName("<script>.pdf")).isEqualTo("script.pdf");
        assertThat(DocumentUploads.cleanName(null)).isEqualTo("documento.pdf");
        assertThat(DocumentUploads.cleanName(".pdf")).isEqualTo("documento.pdf");
    }
}
