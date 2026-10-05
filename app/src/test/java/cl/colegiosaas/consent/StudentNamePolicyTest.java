package cl.colegiosaas.consent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentNamePolicyTest {

    @Test
    void anyGivenNameWithAnySurnameCountsAsAFullName() {
        assertThat(StudentNamePolicy.fullNameForms("María José Pérez Soto"))
                .contains("maria jose perez soto", "maria jose perez", "maria perez", "jose soto")
                .doesNotContain("maria", "perez");
        assertThat(StudentNamePolicy.fullNameForms("Benjamín Muñoz")).contains("benjamin munoz");
        assertThat(StudentNamePolicy.fullNameForms("Cher")).isEmpty();
    }

    @Test
    void comparisonIgnoresAccentsCaseAndPunctuation() {
        assertThat(StudentNamePolicy.normalize("¡MARÍA, Pérez!")).isEqualTo("maria perez");
    }
}
