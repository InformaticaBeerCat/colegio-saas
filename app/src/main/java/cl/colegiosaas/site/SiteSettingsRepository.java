package cl.colegiosaas.site;

import cl.colegiosaas.shared.persistence.SingletonEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Long> {

    default Optional<SiteSettings> findSingleton() {
        return findById(SingletonEntity.ID);
    }

    /** Con logo y favicon cargados, para armar el encabezado sin sesión abierta en la vista. */
    @EntityGraph(attributePaths = {"logo", "logo.file", "logo.blurredFile", "favicon", "favicon.file", "favicon.blurredFile"})
    Optional<SiteSettings> findWithBrandById(Long id);
}
