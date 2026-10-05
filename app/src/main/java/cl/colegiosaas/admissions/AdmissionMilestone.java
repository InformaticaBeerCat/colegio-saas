package cl.colegiosaas.admissions;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Hito del calendario del proceso: "Postulación SAE: 12 al 30 de agosto" (ADM-02). */
@Entity
@Table(name = "admission_milestone")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdmissionMilestone extends BaseEntity {

    private int processYear;

    @NotBlank
    private String name;

    private LocalDate startsOn;

    /** Nulo = hito de un solo día. */
    private LocalDate endsOn;

    private String description;

    private String linkUrl;

    private int sortOrder;

    public AdmissionMilestone(int processYear, String name, LocalDate startsOn, LocalDate endsOn, int sortOrder) {
        if (endsOn != null && endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("El hito no puede terminar antes de empezar");
        }
        this.processYear = processYear;
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.sortOrder = sortOrder;
    }
}
