package cl.colegiosaas.shared.tenant;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolDependency;
import cl.colegiosaas.platform.SchoolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo más importante del multi-tenant: un colegio nunca ve datos de otro.
 * Sin transacción de test: cada llamada al repositorio abre su propia sesión
 * DENTRO del ámbito de colegio, igual que en producción.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MultiTenancyConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantIsolationTest {

    @Autowired
    SchoolRepository schools;

    @Autowired
    UserAccountRepository users;

    long schoolA;
    long schoolB;
    long userB;

    @BeforeEach
    void setUp() {
        schoolA = schools.save(new School("Colegio A", SchoolDependency.PRIVATE_SUBSIDIZED, "colegio-a", Plan.BASE)).getId();
        schoolB = schools.save(new School("Colegio B", SchoolDependency.MUNICIPAL, "colegio-b", Plan.BASE)).getId();

        try (var scope = TenantContext.use(schoolA)) {
            users.save(new UserAccount("ana@colegio-a.cl", "Ana", Role.SCHOOL_ADMIN));
            users.save(new UserAccount("compartido@mail.cl", "Profe en dos colegios", Role.EDITOR));
        }
        try (var scope = TenantContext.use(schoolB)) {
            userB = users.save(new UserAccount("beto@colegio-b.cl", "Beto", Role.EDITOR)).getId();
            users.save(new UserAccount("compartido@mail.cl", "Profe en dos colegios", Role.SCHEDULE_MANAGER));
        }
    }

    @AfterEach
    void tearDown() {
        try (var scope = TenantContext.usePlatform()) {
            users.deleteAll();
            schools.deleteAll();
        }
    }

    @Test
    void eachSchoolSeesOnlyItsOwnUsers() {
        try (var scope = TenantContext.use(schoolA)) {
            assertThat(users.findAll())
                    .extracting(UserAccount::getEmail)
                    .containsExactlyInAnyOrder("ana@colegio-a.cl", "compartido@mail.cl");
            assertThat(users.findByEmail("beto@colegio-b.cl")).isEmpty();
        }
    }

    @Test
    void findByIdDoesNotCrossSchools() {
        try (var scope = TenantContext.use(schoolA)) {
            assertThat(users.findById(userB)).isEmpty();
        }
    }

    @Test
    void sameEmailMayExistInDifferentSchools() {
        try (var scope = TenantContext.use(schoolB)) {
            assertThat(users.findByEmail("compartido@mail.cl"))
                    .get()
                    .satisfies(u -> assertThat(u.getRoles()).containsExactly(Role.SCHEDULE_MANAGER));
        }
    }

    @Test
    void withoutSchoolNothingIsVisible() {
        assertThat(users.findAll()).isEmpty();
        assertThat(users.findById(userB)).isEmpty();
    }

    @Test
    void withoutSchoolNothingCanBeInserted() {
        assertThatThrownBy(() -> users.save(new UserAccount("x@x.cl", "Sin colegio")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void platformModeSeesAllSchools() {
        try (var scope = TenantContext.usePlatform()) {
            assertThat(users.count()).isEqualTo(4);
        }
    }

    @Test
    void scopesRestoreThePreviousSchool() {
        try (var outer = TenantContext.use(schoolA)) {
            try (var inner = TenantContext.use(schoolB)) {
                assertThat(TenantContext.currentSchoolId()).contains(schoolB);
            }
            assertThat(TenantContext.currentSchoolId()).contains(schoolA);
        }
        assertThat(TenantContext.currentSchoolId()).isEmpty();
    }
}
