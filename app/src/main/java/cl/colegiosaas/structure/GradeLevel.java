package cl.colegiosaas.structure;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Nivel que imparte el colegio ("Pre-kínder", "1° Básico", "3° Medio TP").
 * Solo estructura, para filtrar calendario, vacantes, álbumes y reuniones: nada académico.
 */
@Entity
@Table(name = "grade_level")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeLevel extends BaseEntity {

    @NotBlank
    private String name;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private EducationStage stage;

    /** Solo en media; nulo en parvularia y básica. */
    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private SecondaryTrack track;

    /** Orden de menor a mayor (Pre-kínder primero). */
    private int sortOrder;

    public GradeLevel(String name, EducationStage stage, SecondaryTrack track, int sortOrder) {
        this.name = name;
        classify(stage, track);
        this.sortOrder = sortOrder;
    }

    public void classify(EducationStage newStage, SecondaryTrack newTrack) {
        Objects.requireNonNull(newStage, "stage");
        if (newTrack != null && newStage != EducationStage.SECONDARY) {
            throw new IllegalArgumentException("Solo la educación media tiene modalidad (HC/TP/artística)");
        }
        stage = newStage;
        track = newTrack;
    }
}
