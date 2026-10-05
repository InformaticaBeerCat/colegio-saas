package cl.colegiosaas.support;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolDependency;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.persistence.EntityManager;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Datos de prueba persistidos para los tests de repositorio. Se inyecta con @Autowired
 * (lo importa {@link RepositoryTest}); todo queda dentro de la transacción del test.
 */
public class TestFixtures {

    private final EntityManager em;
    private final BlindIndex index;
    private final AtomicInteger sequence = new AtomicInteger();

    public TestFixtures(EntityManager em, BlindIndex index) {
        this.em = em;
        this.index = index;
    }

    public BlindIndex index() {
        return index;
    }

    public UserAccount user(String email, Role... roles) {
        return persist(new UserAccount(email, email.substring(0, email.indexOf('@')), roles));
    }

    public School school() {
        School school = new School("Colegio San José", Plan.COMMUNITY);
        school.setRbd("8485-1");
        school.setDependency(SchoolDependency.PRIVATE_SUBSIDIZED);
        return persist(school);
    }

    public LegalText publishedText(LegalTextKind kind) {
        LegalText text = new LegalText(kind, sequence.incrementAndGet(), "Texto " + kind, "<p>Contenido</p>");
        text.publish(null);
        return persist(text);
    }

    public ConsentRecord consent(String email, ConsentPurpose purpose) {
        return persist(new ConsentRecord(new DataSubject("Ana Pérez", email), purpose,
                publishedText(LegalTextKind.NOTICE_CONTACT), true,
                new RequestOrigin("/contacto", "200.1.2.3", "JUnit"), index));
    }

    public StoredFile file(String name) {
        int n = sequence.incrementAndGet();
        return persist(new StoredFile("files/" + n + "-" + name, name, "application/pdf", 1024, "%064d".formatted(n)));
    }

    public GradeLevel level(String name, EducationStage stage, int sortOrder) {
        return persist(new GradeLevel(name, stage, null, sortOrder));
    }

    public Course course(GradeLevel level, String section, int year) {
        return persist(new Course(level, section, year));
    }

    public <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
