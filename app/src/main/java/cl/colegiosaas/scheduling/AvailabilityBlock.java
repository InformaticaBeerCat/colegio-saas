package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/** Período sin citas: licencia de un gestor, jornada de reflexión del colegio completo (AGE-02). */
@Entity
@Table(name = "availability_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityBlock extends BaseEntity {

    /** Nulo = bloquea a todos los gestores. */
    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount host;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private String reason;

    public AvailabilityBlock(UserAccount host, LocalDateTime startsAt, LocalDateTime endsAt, String reason) {
        if (!Objects.requireNonNull(endsAt, "endsAt").isAfter(Objects.requireNonNull(startsAt, "startsAt"))) {
            throw new IllegalArgumentException("El bloqueo debe terminar después de empezar");
        }
        this.host = host;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public boolean overlaps(LocalDateTime from, LocalDateTime to) {
        return startsAt.isBefore(to) && endsAt.isAfter(from);
    }
}
