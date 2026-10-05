package cl.colegiosaas.scheduling;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Feriado nacional chileno: no se ofrecen citas (AGE-02). Se cargan desde una fuente oficial
 * en la fase 7; no se escriben a mano porque cambian por ley de un año a otro.
 */
@Entity
@Table(name = "holiday")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday extends BaseEntity {

    /** "day" es palabra clave en H2; por eso holiday_date. */
    private LocalDate holidayDate;

    @NotBlank
    private String name;

    /** Feriado irrenunciable. */
    private boolean mandatory;

    public Holiday(LocalDate holidayDate, String name, boolean mandatory) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.mandatory = mandatory;
    }
}
