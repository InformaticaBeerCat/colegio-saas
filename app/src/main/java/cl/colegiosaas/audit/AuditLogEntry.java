package cl.colegiosaas.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * USR-03: quién publicó, editó, borró o vio datos personales. Solo se inserta, nunca se edita;
 * por eso no extiende {@code BaseEntity} (no necesita versión ni fecha de actualización).
 */
@Entity
@Table(name = "audit_log")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    private ActorType actorType;

    /** Id del usuario; nulo para el sistema o un visitante anónimo. */
    private Long actorId;

    /** Copia del nombre al momento del hecho: el registro sobrevive aunque el usuario se borre. */
    private String actorName;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    /** Tipo de entidad afectada, p. ej. "NewsArticle". */
    private String entityType;

    private String entityId;

    private String details;

    private String ipAddress;

    public AuditLogEntry(ActorType actorType, Long actorId, String actorName, AuditAction action,
                         String entityType, String entityId, String details, String ipAddress) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ipAddress = ipAddress;
    }
}
