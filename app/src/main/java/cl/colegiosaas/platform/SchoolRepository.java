package cl.colegiosaas.platform;

import cl.colegiosaas.shared.persistence.SingletonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {

    /** Vacío solo antes de correr el asistente de primer arranque. */
    default Optional<School> findSingleton() {
        return findById(SingletonEntity.ID);
    }
}
