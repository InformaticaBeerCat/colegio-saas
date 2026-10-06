package cl.colegiosaas.privacy;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalTextRepository extends JpaRepository<LegalText, Long> {

    /** Versión vigente: la publicada más reciente. */
    Optional<LegalText> findFirstByKindAndEffectiveFromIsNotNullOrderByVersionNumberDesc(LegalTextKind kind);

    @EntityGraph(attributePaths = "publishedBy")
    List<LegalText> findByKindAndEffectiveFromIsNotNullOrderByVersionNumberDesc(LegalTextKind kind);

    Optional<LegalText> findByKindAndVersionNumber(LegalTextKind kind, int versionNumber);

    /** Para numerar la versión siguiente. */
    Optional<LegalText> findFirstByKindOrderByVersionNumberDesc(LegalTextKind kind);
}
