package cl.colegiosaas.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId, Pageable page);
}
