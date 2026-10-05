package cl.colegiosaas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las mismas migraciones contra MySQL 8.4 real (Testcontainers). Los demás tests usan H2 en modo
 * MySQL, que se parece pero no es igual: aquí se comprueba lo que H2 no garantiza.
 * Se salta solo si Docker no está corriendo.
 */
@EnabledIfDockerAvailable
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MySqlCompatibilityTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsRunAndHibernateValidatesTheSchema() {
        // Si el contexto arrancó, Flyway aplicó todas las migraciones y Hibernate validó cada entidad.
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("8.4");
        assertThat(jdbc.queryForObject("select count(*) from retention_policy", Integer.class)).isEqualTo(8);
    }

    @Test
    void checkConstraintsAreEnforced() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into school (id, version, created_at, updated_at, name, plan, time_zone)
                values (2, 0, now(6), now(6), 'Otro', 'BASE', 'America/Santiago')
                """)).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into menu_item (version, created_at, updated_at, menu, label, page_id, url, sort_order)
                values (0, now(6), now(6), 'HEADER', 'Sin destino', null, null, 0)
                """)).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into album (version, created_at, updated_at, slug, title, visibility, course_id, status, download_allowed)
                values (0, now(6), now(6), 'curso-sin-curso', 'X', 'COURSE', null, 'DRAFT', false)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
