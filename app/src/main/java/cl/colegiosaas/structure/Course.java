package cl.colegiosaas.structure;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Curso de un año académico ("2° Básico B, 2027"). Se usa para álbumes y comunicados por curso,
 * reuniones de apoderados (AGE-08) y el registro mínimo de estudiantes para autorizaciones de imagen.
 */
@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_level_id")
    private GradeLevel gradeLevel;

    /** Letra o identificador ("A", "B"); vacío si el nivel tiene un solo curso. */
    @NotNull
    @Size(max = 5)
    private String section;

    private int academicYear;

    public Course(GradeLevel gradeLevel, String section, int academicYear) {
        this.gradeLevel = Objects.requireNonNull(gradeLevel, "gradeLevel");
        this.section = section == null ? "" : section.strip();
        this.academicYear = academicYear;
    }

    /** "2° Básico B" o "Kínder" si no hay letra. */
    public String displayName() {
        return section.isEmpty() ? gradeLevel.getName() : gradeLevel.getName() + " " + section;
    }
}
