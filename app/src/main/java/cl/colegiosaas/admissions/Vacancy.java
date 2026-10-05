package cl.colegiosaas.admissions;

import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** Vacantes referenciales por nivel y año, actualizables por el admin (ADM-07). */
@Entity
@Table(name = "vacancy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vacancy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private GradeLevel gradeLevel;

    private int academicYear;

    private int seats;

    public Vacancy(GradeLevel gradeLevel, int academicYear, int seats) {
        this.gradeLevel = Objects.requireNonNull(gradeLevel, "gradeLevel");
        this.academicYear = academicYear;
        updateSeats(seats);
    }

    public void updateSeats(int newSeats) {
        if (newSeats < 0) {
            throw new IllegalArgumentException("Las vacantes no pueden ser negativas");
        }
        seats = newSeats;
    }
}
