package cl.colegiosaas.web;

import cl.colegiosaas.documents.DocumentCategory;
import cl.colegiosaas.documents.DocumentService;
import cl.colegiosaas.documents.InstitutionalDocument;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.DocumentUploads;
import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Documentos institucionales con versiones, REX 781, alerta a 12 meses y accesibilidad (DOC-01..05, DOC-07). */
class InstitutionalDocumentsTest extends WebTestSupport {

    @Autowired
    DocumentService documents;

    @Autowired
    DocumentUploads uploads;

    @Autowired
    SchoolRepository schools;

    @Autowired
    PageService pages;

    UserAccount editor;
    UserAccount admin;

    @BeforeEach
    void setUp() {
        install();
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
    }

    @Test
    void internalRegulationsRequireAcademicYearAndShowTheRex781Data() throws Exception {
        InstitutionalDocument ri = documents.create(DocumentCategory.INTERNAL_REGULATIONS, "Reglamento Interno", null, null);

        mvc.perform(multipart("/admin/documents/" + ri.getId() + "/versions")
                        .file(new MockMultipartFile("file", "RI.pdf", "application/pdf", pdf("ri-2026")))
                        .param("lastUpdatedOn", "2026-03-01")
                        .with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("año académico")));

        mvc.perform(multipart("/admin/documents/" + ri.getId() + "/versions")
                        .file(new MockMultipartFile("file", "RI.pdf", "application/pdf", pdf("ri-2026")))
                        .param("lastUpdatedOn", "2026-03-01").param("academicYear", "2026").param("accessiblePdf", "true")
                        .with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("notice", containsString("Versión publicada")));

        mvc.perform(get("/documentos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reglamento Interno")))
                .andExpect(content().string(containsString("<dt>RBD</dt><dd>8485-1</dd>")))
                .andExpect(content().string(containsString("<dt>Año académico</dt><dd>2026</dd>")))
                .andExpect(content().string(containsString("<dd>Colegio San José</dd>")))
                .andExpect(content().string(containsString("1 de marzo de 2026")))
                .andExpect(content().string(not(containsString("lectores de pantalla"))));
    }

    @Test
    void rbdIsRequiredToPublishTheInternalRegulations() {
        schools.findSingleton().orElseThrow().setRbd(null);
        InstitutionalDocument ri = documents.create(DocumentCategory.INTERNAL_REGULATIONS, "Reglamento Interno", null, null);

        assertThatThrownBy(() -> documents.publishVersion(ri.getId(),
                        uploads.storePdf("ri.pdf", pdf("ri")), 2026, LocalDate.of(2026, 3, 1), true, null, editor.getId()))
                .hasMessageContaining("RBD");
    }

    @Test
    void newVersionsArchiveThePreviousOneWhichStaysDownloadable() throws Exception {
        InstitutionalDocument pise = documents.create(DocumentCategory.SCHOOL_SAFETY_PLAN, "Plan Integral de Seguridad Escolar", null, null);
        documents.publishVersion(pise.getId(), uploads.storePdf("pise-2025.pdf", pdf("pise-2025")), 2025,
                LocalDate.of(2025, 4, 1), false, null, editor.getId());
        documents.publishVersion(pise.getId(), uploads.storePdf("pise-2026.pdf", pdf("pise-2026")), 2026,
                LocalDate.of(2026, 4, 1), true, "Nuevas zonas de evacuación", editor.getId());

        InstitutionalDocument reloaded = documents.get(pise.getId());
        assertThat(reloaded.currentVersion().orElseThrow().getAcademicYear()).isEqualTo(2026);
        assertThat(reloaded.archivedVersions()).hasSize(1);

        String page = mvc.perform(get("/documentos/" + reloaded.getSlug()))
                .andExpect(content().string(containsString("Versiones anteriores")))
                .andExpect(content().string(containsString("Versión 2025")))
                .andReturn().getResponse().getContentAsString();
        // Las dos versiones se pueden descargar (el historial es público).
        Matcher links = Pattern.compile("href=\"(/archivos/[0-9a-f]{64}/[^\"]+)\"").matcher(page);
        int found = 0;
        while (links.find()) {
            mvc.perform(get(links.group(1))).andExpect(status().isOk());
            found++;
        }
        assertThat(found).isEqualTo(2);

        mvc.perform(post("/admin/documents/" + pise.getId() + "/delete").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("no se puede eliminar")));
    }

    @Test
    void inaccessiblePdfsWarnAndOfferAnotherFormat() throws Exception {
        schools.findSingleton().orElseThrow().setContactEmail("contacto@colegio.cl");
        InstitutionalDocument plan = documents.create(DocumentCategory.INCLUSION_PLAN, "Plan de Inclusión", null, null);
        documents.publishVersion(plan.getId(), uploads.storePdf("plan.pdf", pdf("plan")), null,
                LocalDate.of(2026, 1, 10), false, null, editor.getId());

        mvc.perform(get("/documentos"))
                .andExpect(content().string(containsString("puede no ser legible con lectores de pantalla")))
                .andExpect(content().string(containsString("mailto:contacto@colegio.cl")));
    }

    @Test
    void documentsOlderThanTwelveMonthsAlertOnTheDashboard() throws Exception {
        InstitutionalDocument ri = documents.create(DocumentCategory.INTERNAL_REGULATIONS, "Reglamento Interno", null, null);
        documents.publishVersion(ri.getId(), uploads.storePdf("ri.pdf", pdf("ri-old")), 2025,
                LocalDate.now().minusMonths(13), true, null, editor.getId());

        assertThat(documents.dueForReview()).extracting(InstitutionalDocument::getTitle).containsExactly("Reglamento Interno");
        mvc.perform(get("/admin").with(as(admin)))
                .andExpect(content().string(containsString("más de 12 meses sin actualizar")));
        mvc.perform(get("/admin/documents").with(as(admin)))
                .andExpect(content().string(containsString("Más de 12 meses sin actualizar")));
    }

    @Test
    void protocolsAreShownInsideTheirInternalRegulations() throws Exception {
        InstitutionalDocument ri = documents.create(DocumentCategory.INTERNAL_REGULATIONS, "Reglamento Interno", null, null);
        InstitutionalDocument protocol = documents.create(DocumentCategory.PROTOCOL, "Protocolo de acoso escolar", null, ri.getId());
        documents.publishVersion(ri.getId(), uploads.storePdf("ri.pdf", pdf("ri")), 2026, LocalDate.of(2026, 3, 1), true, null, editor.getId());
        documents.publishVersion(protocol.getId(), uploads.storePdf("p.pdf", pdf("p")), 2026, LocalDate.of(2026, 3, 1), true, null, editor.getId());

        String html = mvc.perform(get("/documentos")).andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("document-children")).isGreaterThan(html.indexOf("Reglamento Interno"));
        assertThat(html).contains("Protocolo de acoso escolar");
        // Solo los protocolos y anexos van dentro del Reglamento Interno.
        assertThatThrownBy(() ->
                documents.create(DocumentCategory.PEI, "PEI", null, ri.getId())).hasMessageContaining("protocolos y anexos");
    }

    @Test
    void coexistencePageListsProtocolsAndTheReportChannel() throws Exception {
        InstitutionalDocument protocol = documents.create(DocumentCategory.PROTOCOL, "Protocolo de maltrato", null, null);
        documents.publishVersion(protocol.getId(), uploads.storePdf("m.pdf", pdf("m")), 2026, LocalDate.of(2026, 3, 1), true, null, editor.getId());
        Page page = pages.create("Convivencia escolar", "convivencia-escolar", PageKind.COEXISTENCE);
        pages.addBlock(page.getId(), new Block.Documents("Protocolos", List.of(DocumentCategory.PROTOCOL)));
        pages.addBlock(page.getId(), new Block.ReportChannel("Canal de denuncia", "Avísanos con confianza.",
                "convivencia@colegio.cl", "+56 2 2345 6789", "Oficina de convivencia", null));
        pages.publish(page.getId());

        mvc.perform(get("/convivencia-escolar"))
                .andExpect(content().string(containsString("Protocolo de maltrato")))
                .andExpect(content().string(containsString("mailto:convivencia@colegio.cl")))
                .andExpect(content().string(containsString("Presencial: Oficina de convivencia")));
    }

    @Test
    void nonPdfFilesAndMissingFilesAreNotServed() throws Exception {
        mvc.perform(get("/archivos/" + "a".repeat(64) + "/x.pdf")).andExpect(status().isNotFound());
        // Un PDF subido pero no publicado en ninguna parte tampoco se entrega.
        String sha = uploads.storePdf("borrador.pdf", pdf("borrador")).getSha256();
        mvc.perform(get("/archivos/" + sha + "/borrador.pdf")).andExpect(status().isNotFound());
    }
}
