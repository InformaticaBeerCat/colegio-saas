package cl.colegiosaas.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchoolTest {

    @Test
    void newSchoolGetsTheFeaturesOfItsPlan() {
        School school = new School("Colegio", Plan.COMMUNITY);

        assertThat(school.getFeatures()).isEqualTo(Plan.COMMUNITY.includedFeatures());
    }

    @Test
    void downgradingThePlanKeepsAddOns() {
        School school = new School("Colegio", Plan.COMMUNITY);
        school.enableFeature(Feature.HOSTED_VIDEO);

        school.changePlan(Plan.BASE);

        assertThat(school.hasFeature(Feature.SCHEDULING)).isFalse();
        assertThat(school.hasFeature(Feature.NEWS)).isTrue();
        assertThat(school.hasFeature(Feature.HOSTED_VIDEO)).isTrue();
    }

    @Test
    void setupCannotFinishWithoutDependency() {
        School school = new School("Colegio", Plan.BASE);

        assertThatThrownBy(school::completeSetup).isInstanceOf(IllegalStateException.class);

        school.setDependency(SchoolDependency.SLEP);
        school.completeSetup();
        assertThat(school.isSetupCompleted()).isTrue();
    }

    @Test
    void onlyPaidPrivateSchoolsSkipSae() {
        assertThat(SchoolDependency.PRIVATE_PAID.admitsViaSae()).isFalse();
        assertThat(SchoolDependency.PRIVATE_SUBSIDIZED.admitsViaSae()).isTrue();
        assertThat(SchoolDependency.SLEP.admitsViaSae()).isTrue();
    }
}
