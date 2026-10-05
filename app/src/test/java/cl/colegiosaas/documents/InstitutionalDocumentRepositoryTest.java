package cl.colegiosaas.documents;

import cl.colegiosaas.documents.InstitutionalDocument.VersionDetails;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class InstitutionalDocumentRepositoryTest {

    @Autowired
    InstitutionalDocumentRepository documents;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    @Test
    void newVersionArchivesThePreviousOneAndKeepsTheEvidence() {
        School school = fixtures.school();
        UserAccount admin = fixtures.user("admin@colegio.cl", Role.SCHOOL_ADMIN);
        InstitutionalDocument regulations = new InstitutionalDocument(
                DocumentCategory.INTERNAL_REGULATIONS, "Reglamento Interno", "reglamento-interno");
        regulations.publishVersion(details(2026, LocalDate.of(2026, 3, 1)), school, admin);
        documents.saveAndFlush(regulations);

        school.setName("Colegio San José de Ñuñoa");
        regulations.publishVersion(details(2027, LocalDate.of(2027, 3, 1)), school, admin);
        em.flush();
        em.clear();

        InstitutionalDocument loaded = documents.findBySlug("reglamento-interno").orElseThrow();
        DocumentVersion current = loaded.currentVersion().orElseThrow();
        assertThat(current.getAcademicYear()).isEqualTo(2027);
        assertThat(current.getSchoolName()).isEqualTo("Colegio San José de Ñuñoa");
        assertThat(current.getRbd()).isEqualTo("8485-1");
        assertThat(loaded.archivedVersions())
                .singleElement()
                .satisfies(old -> assertThat(old.getSchoolName()).isEqualTo("Colegio San José"));
    }

    @Test
    void internalRegulationsRequireAcademicYearAndRbd() {
        School school = fixtures.school();
        InstitutionalDocument regulations = new InstitutionalDocument(
                DocumentCategory.INTERNAL_REGULATIONS, "Reglamento Interno", "reglamento-interno");

        assertThatThrownBy(() -> regulations.publishVersion(details(null, LocalDate.now()), school, null))
                .isInstanceOf(IllegalArgumentException.class);

        school.setRbd(null);
        assertThatThrownBy(() -> regulations.publishVersion(details(2026, LocalDate.now()), school, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void otherDocumentsDoNotNeedRegulatoryMetadata() {
        School school = fixtures.school();
        school.setRbd(null);
        InstitutionalDocument pise = new InstitutionalDocument(DocumentCategory.SCHOOL_SAFETY_PLAN, "PISE", "pise");

        pise.publishVersion(details(null, LocalDate.of(2026, 4, 1)), school, null);

        assertThat(pise.currentVersion()).isPresent();
    }

    @Test
    void documentNeedsReviewAfterTwelveMonths() {
        InstitutionalDocument pei = new InstitutionalDocument(DocumentCategory.PEI, "PEI", "pei");
        pei.publishVersion(details(2025, LocalDate.of(2025, 10, 1)), fixtures.school(), null);

        assertThat(pei.needsAnnualReview(LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(pei.needsAnnualReview(LocalDate.of(2026, 10, 1))).isTrue();
    }

    private VersionDetails details(Integer year, LocalDate lastUpdated) {
        return new VersionDetails(fixtures.file("documento.pdf"), year, lastUpdated, true, null);
    }
}
