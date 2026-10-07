package cl.colegiosaas.admissions;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProspectRepository extends JpaRepository<Prospect, Long> {

    List<Prospect> findByEmailHash(String emailHash);

    /** Para la tarea de retención (PRV-06). */
    List<Prospect> findByRetainUntilBefore(LocalDate date);

    /** Conteo por etapa para el embudo (REP-02). */
    @Query("select p.stage, count(p) from Prospect p where p.entryYear = :entryYear group by p.stage")
    List<Object[]> countByStage(int entryYear);

    @EntityGraph(attributePaths = "gradeLevel")
    List<Prospect> findTop300ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"gradeLevel", "consent", "followUpConsent"})
    Optional<Prospect> findWithLevelById(Long id);

    long countByStage(ProspectStage stage);
}
