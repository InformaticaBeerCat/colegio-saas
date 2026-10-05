package cl.colegiosaas.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void longPassphrasesAreAccepted() {
        assertThat(PasswordPolicy.problems("la cordillera nevada en julio", "ana@colegio.cl")).isEmpty();
    }

    @Test
    void shortRepeatedCommonOrPersonalPasswordsAreRejected() {
        assertThat(PasswordPolicy.problems("corta", null)).hasSize(1);
        assertThat(PasswordPolicy.problems("aaaaaaaaaaaaaaaa", null)).isNotEmpty();
        assertThat(PasswordPolicy.problems("password1234", null)).isNotEmpty();
        assertThat(PasswordPolicy.problems("soy ana.perez y esta es mi clave", "ana.perez@colegio.cl")).isNotEmpty();
    }
}
