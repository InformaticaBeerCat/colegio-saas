package cl.colegiosaas.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Una instalación = un colegio: la tabla school admite una sola fila. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchoolRepositoryTest {

    @Autowired
    SchoolRepository schools;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savesAndReloadsTheSchoolProfile() {
        School school = new School("Colegio San José", Plan.COMMUNITY);
        school.setRbd("8485-1");
        school.setDependency(SchoolDependency.PRIVATE_SUBSIDIZED);
        school.setAddress(new Address("Av. Siempre Viva 123", "Ñuñoa", "Metropolitana", -33.45, -70.6));
        schools.saveAndFlush(school);
        em.clear();

        School loaded = schools.findSingleton().orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Colegio San José");
        assertThat(loaded.getAddress().commune()).isEqualTo("Ñuñoa");
        assertThat(loaded.getFeatures()).isEqualTo(Plan.COMMUNITY.includedFeatures());
    }

    @Test
    void secondSchoolProfileIsRejected() {
        schools.saveAndFlush(new School("Primero", Plan.BASE));
        em.clear();

        assertThatThrownBy(() -> schools.saveAndFlush(new School("Segundo", Plan.BASE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAnyIdOtherThanOne() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into school (id, version, created_at, updated_at, name, plan, time_zone)
                values (2, 0, current_timestamp, current_timestamp, 'Otro', 'BASE', 'America/Santiago')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
