package cl.colegiosaas.privacy;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de una brecha de seguridad (PRV-09): qué pasó, qué datos afectó y cuándo se notificó
 * a la autoridad y a los titulares. La plantilla de notificación vive en la fase 6.
 */
@Entity
@Table(name = "security_incident")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityIncident extends BaseEntity {

    @NotBlank
    private String title;

    /** Columna LONGTEXT. */
    private String description;

    @Setter(AccessLevel.NONE)
    private Instant detectedAt;

    @Enumerated(EnumType.STRING)
    private IncidentSeverity severity;

    /** Tipos de datos afectados, p. ej. "emails y teléfonos de apoderados". */
    private String affectedData;

    private Integer affectedSubjectsEstimate;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Setter(AccessLevel.NONE)
    private Instant containedAt;

    @Setter(AccessLevel.NONE)
    private Instant closedAt;

    @Setter(AccessLevel.NONE)
    private Instant authorityNotifiedAt;

    @Setter(AccessLevel.NONE)
    private Instant subjectsNotifiedAt;

    /** Columna LONGTEXT. */
    private String actionsTaken;

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private UserAccount reportedBy;

    public SecurityIncident(String title, Instant detectedAt, IncidentSeverity severity, UserAccount reportedBy) {
        this.title = title;
        this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
        this.severity = severity;
        this.reportedBy = reportedBy;
    }

    public void markContained(Instant at) {
        containedAt = at;
        status = IncidentStatus.CONTAINED;
    }

    public void close(Instant at) {
        closedAt = at;
        status = IncidentStatus.CLOSED;
    }

    public void recordAuthorityNotification(Instant at) {
        authorityNotifiedAt = at;
    }

    public void recordSubjectsNotification(Instant at) {
        subjectsNotifiedAt = at;
    }
}
