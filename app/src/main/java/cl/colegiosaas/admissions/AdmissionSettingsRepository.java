package cl.colegiosaas.admissions;

import cl.colegiosaas.shared.persistence.SingletonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdmissionSettingsRepository extends JpaRepository<AdmissionSettings, Long> {

    default Optional<AdmissionSettings> findSingleton() {
        return findById(SingletonEntity.ID);
    }
}
