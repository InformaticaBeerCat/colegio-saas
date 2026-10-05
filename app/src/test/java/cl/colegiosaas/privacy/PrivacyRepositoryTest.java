package cl.colegiosaas.privacy;

import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class PrivacyRepositoryTest {

    @Autowired
    ConsentRecordRepository consents;

    @Autowired
    LegalTextRepository legalTexts;

    @Autowired
    DataSubjectRequestRepository requests;

    @Autowired
    RetentionPolicyRepository retention;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    final RequestOrigin origin = new RequestOrigin("/contacto", "200.1.2.3", "JUnit");

    @Test
    void consentIsEncryptedAtRestAndFoundByBlindIndex() {
        LegalText notice = fixtures.publishedText(LegalTextKind.NOTICE_CONTACT);
        consents.save(new ConsentRecord(new DataSubject("Ana Pérez", "Ana@Mail.cl"), ConsentPurpose.CONTACT,
                notice, true, origin, fixtures.index()));
        consents.save(new ConsentRecord(new DataSubject("Ana Pérez", "ana@mail.cl"), ConsentPurpose.NEWSLETTER,
                notice, false, origin, fixtures.index()));
        em.flush();
        em.clear();

        List<String> raw = jdbc.queryForList("select subject_email from consent_record", String.class);
        assertThat(raw).allSatisfy(value -> assertThat(value).startsWith("v1:").doesNotContainIgnoringCase("ana@mail.cl"));

        List<ConsentRecord> found = consents.findBySubjectEmailHashOrderByCreatedAtDesc(fixtures.index().of("ana@mail.cl"));
        assertThat(found).hasSize(2).extracting(ConsentRecord::getSubjectName).containsOnly("Ana Pérez");
        assertThat(found).filteredOn(ConsentRecord::isActive).extracting(ConsentRecord::getPurpose)
                .containsExactly(ConsentPurpose.CONTACT);
    }

    @Test
    void consentRequiresAPublishedTextAndPublishedTextsAreFrozen() {
        LegalText draft = fixtures.persist(new LegalText(LegalTextKind.PRIVACY_POLICY, 1, "Política", "<p>v1</p>"));

        assertThatThrownBy(() -> new ConsentRecord(new DataSubject("Ana", "ana@mail.cl"), ConsentPurpose.CONTACT,
                draft, true, origin, fixtures.index())).isInstanceOf(IllegalArgumentException.class);

        draft.publish(null);
        assertThatThrownBy(() -> draft.edit("Política", "<p>cambio</p>")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void currentLegalTextIsTheLatestPublishedVersion() {
        LegalText v1 = new LegalText(LegalTextKind.COOKIE_POLICY, 1, "Cookies", "v1");
        v1.publish(null);
        LegalText v2 = new LegalText(LegalTextKind.COOKIE_POLICY, 2, "Cookies", "v2");
        v2.publish(null);
        LegalText v3draft = new LegalText(LegalTextKind.COOKIE_POLICY, 3, "Cookies", "v3");
        legalTexts.saveAll(List.of(v1, v2, v3draft));

        assertThat(legalTexts.findFirstByKindAndEffectiveFromIsNotNullOrderByVersionNumberDesc(LegalTextKind.COOKIE_POLICY))
                .get().extracting(LegalText::getVersionNumber).isEqualTo(2);
        assertThat(legalTexts.findFirstByKindOrderByVersionNumberDesc(LegalTextKind.COOKIE_POLICY))
                .get().extracting(LegalText::getVersionNumber).isEqualTo(3);
    }

    @Test
    void dataSubjectRequestsTrackTheirDeadline() {
        DataSubjectRequest request = requests.save(new DataSubjectRequest(DataSubjectRight.ERASURE,
                new DataSubject("Ana", "ana@mail.cl"), true, "Borrar las fotos de mi hija", LocalDate.of(2026, 11, 4),
                fixtures.index()));

        assertThat(request.getTrackingCode()).matches("D-[2-9A-Z]{8}");
        assertThat(request.isOverdue(LocalDate.of(2026, 11, 4))).isFalse();
        assertThat(request.isOverdue(LocalDate.of(2026, 11, 5))).isTrue();

        request.complete("Fotos retiradas y datos borrados");
        assertThat(request.isOverdue(LocalDate.of(2026, 12, 1))).isFalse();
        assertThatThrownBy(() -> request.reject("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retentionPoliciesComeSeededByTheMigration() {
        assertThat(retention.findAll()).hasSize(RetentionCategory.values().length);
        assertThat(retention.findByDataCategory(RetentionCategory.PROSPECTS))
                .get().extracting(RetentionPolicy::getRetentionDays).isEqualTo(365);
    }
}
