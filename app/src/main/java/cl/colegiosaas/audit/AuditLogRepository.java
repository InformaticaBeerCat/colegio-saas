package cl.colegiosaas.audit;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId, Pageable page);

    Page<AuditLogEntry> findAllByOrderByOccurredAtDescIdDesc(Pageable page);

    List<AuditLogEntry> findByActionOrderByIdAsc(AuditAction action);

    /** Única forma de quitar entradas: por antigüedad, según la política de retención (PRV-06). */
    @Modifying
    @Query("delete from AuditLogEntry e where e.occurredAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
