package cl.colegiosaas.privacy;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, Long> {

    List<SecurityIncident> findAllByOrderByDetectedAtDesc();

    long countByStatusNot(IncidentStatus status);

    @EntityGraph(attributePaths = "reportedBy")
    Optional<SecurityIncident> findWithReporterById(Long id);
}
