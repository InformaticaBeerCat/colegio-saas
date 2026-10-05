package cl.colegiosaas.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolTest {

    @Test
    void newSchoolGetsTheFeaturesOfItsPlan() {
        School school = new School("Colegio", SchoolDependency.PRIVATE_PAID, "Colegio-X", Plan.COMMUNITY);

        assertThat(school.getSubdomain()).isEqualTo("colegio-x");
        assertThat(school.getFeatures()).isEqualTo(Plan.COMMUNITY.includedFeatures());
    }

    @Test
    void downgradingThePlanKeepsAddOns() {
        School school = new School("Colegio", SchoolDependency.SLEP, "colegio", Plan.COMMUNITY);
        school.enableFeature(Feature.HOSTED_VIDEO);

        school.changePlan(Plan.BASE);

        assertThat(school.hasFeature(Feature.SCHEDULING)).isFalse();
        assertThat(school.hasFeature(Feature.NEWS)).isTrue();
        assertThat(school.hasFeature(Feature.HOSTED_VIDEO)).isTrue();
    }

    @Test
    void onlyPaidPrivateSchoolsSkipSae() {
        assertThat(SchoolDependency.PRIVATE_PAID.admitsViaSae()).isFalse();
        assertThat(SchoolDependency.PRIVATE_SUBSIDIZED.admitsViaSae()).isTrue();
        assertThat(SchoolDependency.SLEP.admitsViaSae()).isTrue();
    }
}
