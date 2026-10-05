package cl.colegiosaas.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, Long> {

    Optional<DataSubjectRequest> findByTrackingCode(String trackingCode);

    /** Bandeja del encargado, lo que vence primero arriba. */
    List<DataSubjectRequest> findByStatusInOrderByDueOnAsc(Collection<DataSubjectRequestStatus> statuses);
}
