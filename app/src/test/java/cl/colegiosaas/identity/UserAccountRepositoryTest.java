package cl.colegiosaas.identity;

import cl.colegiosaas.support.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class UserAccountRepositoryTest {

    @Autowired
    UserAccountRepository users;

    @Autowired
    TestEntityManager em;

    @Test
    void findsByEmailIgnoringTheCaseUsedAtCreation() {
        users.saveAndFlush(new UserAccount("Ana@Colegio.cl", "Ana", Role.SCHOOL_ADMIN, Role.EDITOR));
        em.clear();

        UserAccount loaded = users.findByEmail("ana@colegio.cl").orElseThrow();
        assertThat(loaded.getRoles()).containsExactlyInAnyOrder(Role.SCHOOL_ADMIN, Role.EDITOR);
        assertThat(loaded.requiresMfa()).isTrue();
    }

    @Test
    void emailIsUnique() {
        users.saveAndFlush(new UserAccount("ana@colegio.cl", "Ana"));

        assertThatThrownBy(() -> users.saveAndFlush(new UserAccount("ana@colegio.cl", "Otra Ana")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deactivationRemovesAccessImmediately() {
        UserAccount user = new UserAccount("beto@colegio.cl", "Beto", Role.EDITOR);
        user.setPasswordHash("{bcrypt}hash");
        user.activate();
        users.saveAndFlush(user);

        // Entidad ya administrada: basta modificarla, Hibernate detecta el cambio al hacer flush.
        // No llamar save() aquí: en Hibernate 7.4 un merge que deja una colección vacía pierde el DELETE.
        user.deactivate();
        em.flush();
        em.clear();

        UserAccount loaded = users.findByEmail("beto@colegio.cl").orElseThrow();
        assertThat(loaded.canLogIn()).isFalse();
        assertThat(loaded.getRoles()).isEmpty();
        assertThat(loaded.getPasswordHash()).isNull();
        assertThat(loaded.getDeactivatedAt()).isNotNull();
    }
}
