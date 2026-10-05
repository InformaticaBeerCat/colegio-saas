package cl.colegiosaas.platform;

import cl.colegiosaas.shared.persistence.SingletonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {

    /** Consulta liviana (sin cargar la entidad) para saber si la instalación está lista. */
    boolean existsByIdAndSetupCompletedAtIsNotNull(Long id);

    /** Vacío solo antes de correr el asistente de primer arranque. */
    default Optional<School> findSingleton() {
        return findById(SingletonEntity.ID);
    }
}
