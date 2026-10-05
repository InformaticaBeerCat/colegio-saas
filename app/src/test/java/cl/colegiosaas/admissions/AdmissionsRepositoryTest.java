package cl.colegiosaas.admissions;

import cl.colegiosaas.platform.SchoolDependency;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class AdmissionsRepositoryTest {

    @Autowired
    AdmissionSettingsRepository settings;

    @Autowired
    ProspectRepository prospects;

    @Autowired
    VacancyRepository vacancies;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    @Test
    void defaultModeDependsOnTheSchoolDependency() {
        assertThat(AdmissionSettings.defaultFor(SchoolDependency.PRIVATE_SUBSIDIZED, 2027).getMode()).isEqualTo(AdmissionMode.SAE);
        assertThat(AdmissionSettings.defaultFor(SchoolDependency.PRIVATE_PAID, 2027).getMode()).isEqualTo(AdmissionMode.OWN);
    }

    @Test
    void configurableRulesSurviveAJsonRoundTrip() {
        GradeLevel prekinder = fixtures.level("Pre-kínder", EducationStage.EARLY_CHILDHOOD, 1);
        em.flush();
        AdmissionSettings admission = AdmissionSettings.defaultFor(SchoolDependency.SLEP, 2027);
        admission.setRules(new AdmissionRules(List.of(new AdmissionRules.LevelRule(
                prekinder.getId(), true, LocalDate.of(2022, 4, 1), LocalDate.of(2023, 3, 31)))));
        settings.saveAndFlush(admission);
        em.clear();

        AdmissionRules rules = settings.findSingleton().orElseThrow().getRules();
        AdmissionRules.LevelRule rule = rules.forGradeLevel(prekinder.getId()).orElseThrow();
        assertThat(rule.admitsBirthDate(LocalDate.of(2022, 9, 15))).isTrue();
        assertThat(rule.admitsBirthDate(LocalDate.of(2023, 4, 1))).isFalse();
    }

    @Test
    void funnelOnlyMovesForwardAndDiscardingClosesIt() {
        Prospect prospect = newProspect();

        prospect.advanceTo(ProspectStage.VISITED);
        assertThatThrownBy(() -> prospect.advanceTo(ProspectStage.INTERESTED)).isInstanceOf(IllegalArgumentException.class);

        prospect.advanceTo(ProspectStage.DISCARDED);
        assertThatThrownBy(() -> prospect.advanceTo(ProspectStage.APPLIED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiredProspectsAreFoundForDeletion() {
        prospects.save(newProspect());
        em.flush();

        assertThat(prospects.findByRetainUntilBefore(LocalDate.of(2027, 10, 6))).hasSize(1);
        assertThat(prospects.findByRetainUntilBefore(LocalDate.of(2027, 10, 5))).isEmpty();
        assertThat(prospects.findByEmailHash(fixtures.index().of("familia@mail.cl"))).hasSize(1);
    }

    @Test
    void followUpEmailsNeedTheirOwnConsent() {
        Prospect prospect = newProspect();
        assertThat(prospect.acceptsFollowUp()).isFalse();

        prospect.setFollowUpConsent(fixtures.consent("familia@mail.cl", ConsentPurpose.ADMISSIONS_FOLLOW_UP));
        assertThat(prospect.acceptsFollowUp()).isTrue();
    }

    @Test
    void oneVacancyRowPerLevelAndYear() {
        GradeLevel kinder = fixtures.level("Kínder", EducationStage.EARLY_CHILDHOOD, 2);
        vacancies.saveAndFlush(new Vacancy(kinder, 2027, 30));

        assertThatThrownBy(() -> vacancies.saveAndFlush(new Vacancy(kinder, 2027, 10)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> new Vacancy(kinder, 2028, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private Prospect newProspect() {
        return new Prospect(new DataSubject("Familia Rojas", "familia@mail.cl"), "+56933333333", null, 2027,
                ProspectSource.WEBSITE_FORM, fixtures.consent("familia@mail.cl", ConsentPurpose.ADMISSIONS),
                LocalDate.of(2027, 10, 5), fixtures.index());
    }
}
