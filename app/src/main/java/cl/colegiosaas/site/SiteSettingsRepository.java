package cl.colegiosaas.site;

import cl.colegiosaas.shared.persistence.SingletonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Long> {

    default Optional<SiteSettings> findSingleton() {
        return findById(SingletonEntity.ID);
    }
}
