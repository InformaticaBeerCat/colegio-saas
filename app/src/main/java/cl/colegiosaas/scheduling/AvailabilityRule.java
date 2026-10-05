package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Disponibilidad semanal de un gestor (AGE-02): "los martes de 9:00 a 12:30".
 * Las citas se generan en bloques de la duración del tipo dentro de esta ventana.
 */
@Entity
@Table(name = "availability_rule")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Setter(AccessLevel.NONE)
    private UserAccount host;

    /** Nulo = vale para cualquier tipo de cita que atienda el gestor. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppointmentType appointmentType;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private DayOfWeek dayOfWeek;

    @Setter(AccessLevel.NONE)
    private LocalTime startTime;

    @Setter(AccessLevel.NONE)
    private LocalTime endTime;

    private LocalDate validFrom;

    private LocalDate validUntil;

    public AvailabilityRule(UserAccount host, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.host = Objects.requireNonNull(host, "host");
        changeWindow(dayOfWeek, startTime, endTime);
    }

    public void changeWindow(DayOfWeek day, LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("La hora de término debe ser posterior a la de inicio");
        }
        dayOfWeek = Objects.requireNonNull(day, "dayOfWeek");
        startTime = start;
        endTime = end;
    }

    public boolean appliesOn(LocalDate date) {
        return date.getDayOfWeek() == dayOfWeek
                && (validFrom == null || !date.isBefore(validFrom))
                && (validUntil == null || !date.isAfter(validUntil));
    }
}
