package cl.colegiosaas.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registro de auditoría (USR-03). Los servicios lo llaman en cada acción relevante; corre en la
 * misma transacción, así una acción revertida no deja un registro de algo que no pasó.
 */
@Service
public class AuditTrail {

    private final AuditLogRepository entries;

    AuditTrail(AuditLogRepository entries) {
        this.entries = entries;
    }

    /** Actor: el usuario con sesión iniciada (o anónimo si no hay). */
    @Transactional
    public void record(AuditAction action, String entityType, Object entityId, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuditActor actor) {
            save(ActorType.USER, actor.getId(), actor.getName(), action, entityType, entityId, details);
        } else {
            save(ActorType.ANONYMOUS, null, null, action, entityType, entityId, details);
        }
    }

    /** Actor explícito: p. ej. un intento de ingreso, cuando todavía no hay sesión. */
    @Transactional
    public void recordFor(AuditActor actor, AuditAction action, String entityType, Object entityId, String details) {
        save(ActorType.USER, actor.getId(), actor.getName(), action, entityType, entityId, details);
    }

    /** Acción de una tarea automática (publicación programada, borrado por retención…). */
    @Transactional
    public void recordSystem(AuditAction action, String entityType, Object entityId, String details) {
        save(ActorType.SYSTEM, null, null, action, entityType, entityId, details);
    }

    @Transactional
    public void recordAnonymous(AuditAction action, String entityType, Object entityId, String details) {
        save(ActorType.ANONYMOUS, null, null, action, entityType, entityId, details);
    }

    private void save(ActorType type, Long actorId, String actorName, AuditAction action,
                      String entityType, Object entityId, String details) {
        entries.save(new AuditLogEntry(type, actorId, actorName, action, entityType,
                entityId == null ? null : String.valueOf(entityId), details, currentIp()));
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
