package cl.colegiosaas.privacy;

import java.time.Instant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, Long> {

    Optional<DataSubjectRequest> findByTrackingCode(String trackingCode);

    /** Bandeja del encargado, lo que vence primero arriba. */
    @EntityGraph(attributePaths = "handledBy")
    List<DataSubjectRequest> findByStatusInOrderByDueOnAsc(Collection<DataSubjectRequestStatus> statuses);

    List<DataSubjectRequest> findByRequesterEmailHashOrderByCreatedAtDesc(String requesterEmailHash);

    long countByStatusIn(Collection<DataSubjectRequestStatus> statuses);

    /** Cerradas hace más que el plazo de conservación y aún sin anonimizar (PRV-06). */
    List<DataSubjectRequest> findByStatusInAndResolvedAtBeforeAndRequesterEmailHashNot(
            Collection<DataSubjectRequestStatus> statuses, Instant cutoff, String excludedHash);

    @EntityGraph(attributePaths = "handledBy")
    List<DataSubjectRequest> findTop50ByStatusInOrderByResolvedAtDesc(Collection<DataSubjectRequestStatus> statuses);

    @EntityGraph(attributePaths = "handledBy")
    Optional<DataSubjectRequest> findWithHandlerById(Long id);
}
