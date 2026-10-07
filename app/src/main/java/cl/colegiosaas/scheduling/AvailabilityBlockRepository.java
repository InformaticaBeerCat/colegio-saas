package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, Long> {

    /** Bloqueos del gestor o de todo el colegio que se cruzan con el rango. */
    @Query("""
            select b from AvailabilityBlock b
            where (b.host = :host or b.host is null)
              and b.startsAt < :to and b.endsAt > :from
            """)
    List<AvailabilityBlock> findAffecting(UserAccount host, LocalDateTime from, LocalDateTime to);

    /** Bloqueos vigentes o futuros que ve un gestor: los suyos y los de todo el colegio. */
    @EntityGraph(attributePaths = "host")
    @Query("""
            select b from AvailabilityBlock b
            where (b.host = :host or b.host is null) and b.endsAt > :from
            order by b.startsAt
            """)
    List<AvailabilityBlock> findUpcomingFor(UserAccount host, LocalDateTime from);

    @EntityGraph(attributePaths = "host")
    List<AvailabilityBlock> findByEndsAtAfterOrderByStartsAtAsc(LocalDateTime from);
}
