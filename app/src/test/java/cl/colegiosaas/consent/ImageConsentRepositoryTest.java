package cl.colegiosaas.consent;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaKind;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class ImageConsentRepositoryTest {

    @Autowired
    StudentRepository students;

    @Autowired
    ImageConsentRepository consents;

    @Autowired
    StudentAppearanceRepository appearances;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    Course course;
    LegalText form;
    UserAccount manager;

    @BeforeEach
    void setUp() {
        course = fixtures.course(fixtures.level("3° Básico", EducationStage.PRIMARY, 3), "A", 2026);
        form = fixtures.publishedText(LegalTextKind.IMAGE_CONSENT_FORM);
        manager = fixtures.user("consentimientos@colegio.cl", Role.CONSENT_MANAGER);
    }

    @Test
    void studentNamesAreEncryptedAndRegistryHasNoRun() {
        students.saveAndFlush(new Student("Sofía Ramírez Soto", course, "apoderado@mail.cl", fixtures.index()));
        em.clear();

        assertThat(jdbc.queryForObject("select full_name from student", String.class)).startsWith("v1:");
        assertThat(students.findByCourseAndActiveTrue(course)).extracting(Student::getFullName)
                .containsExactly("Sofía Ramírez Soto");
        assertThat(students.findByGuardianEmailHash(fixtures.index().of("APODERADO@mail.cl"))).hasSize(1);
    }

    @Test
    void revokingKeepsHistoryAndLeavesNoActiveConsent() {
        Student student = students.save(new Student("Sofía Ramírez", course, null, fixtures.index()));
        ImageConsent website = consents.save(grant(student, ConsentChannel.WEBSITE));
        consents.save(grant(student, ConsentChannel.SOCIAL_MEDIA));

        website.revoke("El apoderado retiró la autorización por correo");
        em.flush();

        assertThat(consents.findFirstByStudentAndChannelAndRevokedAtIsNull(student, ConsentChannel.WEBSITE)).isEmpty();
        assertThat(consents.findFirstByStudentAndChannelAndRevokedAtIsNull(student, ConsentChannel.SOCIAL_MEDIA)).isPresent();
        assertThat(consents.findByStudentOrderByGrantedAtDesc(student)).hasSize(2);
        assertThatThrownBy(() -> website.revoke("otra vez")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consentMustReferToThePublishedImageForm() {
        Student student = students.save(new Student("Sofía", course, null, fixtures.index()));
        LegalText wrongText = fixtures.publishedText(LegalTextKind.PRIVACY_POLICY);

        assertThatThrownBy(() -> new ImageConsent(student, ConsentChannel.WEBSITE, "Apoderado", ConsentMethod.PAPER_FORM,
                wrongText, null, manager, Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revocationFindsEveryPhotoWhereTheStudentAppears() {
        Student student = students.save(new Student("Sofía", course, null, fixtures.index()));
        Student other = students.save(new Student("Tomás", course, null, fixtures.index()));
        MediaAsset photo1 = fixtures.persist(MediaAsset.upload(MediaKind.IMAGE, fixtures.file("1.jpg"), manager));
        MediaAsset photo2 = fixtures.persist(MediaAsset.upload(MediaKind.IMAGE, fixtures.file("2.jpg"), manager));
        appearances.save(new StudentAppearance(student, photo1, manager));
        appearances.save(new StudentAppearance(student, photo2, manager));
        appearances.save(new StudentAppearance(other, photo2, manager));
        em.flush();

        assertThat(appearances.findAssetsShowing(student)).containsExactlyInAnyOrder(photo1, photo2);
        assertThat(appearances.findStudentsIn(photo2)).containsExactlyInAnyOrder(student, other);
    }

    private ImageConsent grant(Student student, ConsentChannel channel) {
        return new ImageConsent(student, channel, "María Soto", ConsentMethod.PAPER_FORM, form,
                fixtures.file("autorizacion.pdf"), manager, Instant.now());
    }
}
