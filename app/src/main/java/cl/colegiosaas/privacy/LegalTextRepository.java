package cl.colegiosaas.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegalTextRepository extends JpaRepository<LegalText, Long> {

    /** Versión vigente: la publicada más reciente. */
    Optional<LegalText> findFirstByKindAndEffectiveFromIsNotNullOrderByVersionNumberDesc(LegalTextKind kind);

    /** Para numerar la versión siguiente. */
    Optional<LegalText> findFirstByKindOrderByVersionNumberDesc(LegalTextKind kind);
}
