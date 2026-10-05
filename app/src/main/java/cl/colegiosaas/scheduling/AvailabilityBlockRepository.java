package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
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
}
