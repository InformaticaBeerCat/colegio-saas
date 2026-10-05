package cl.colegiosaas.consent;

import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.Course;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Registro mínimo de un estudiante, solo para gestionar autorizaciones de imagen.
 * Sin RUN ni datos académicos (PRV-08): nombre y curso, cifrados donde corresponde.
 */
@Entity
@Table(name = "student")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends BaseEntity {

    @Convert(converter = EncryptedStringConverter.class)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    /** Para avisar al apoderado y, en v2, vincularlo con su cuenta de la zona comunidad. */
    @Convert(converter = EncryptedStringConverter.class)
    private String guardianEmail;

    private String guardianEmailHash;

    private boolean active = true;

    public Student(String fullName, Course course, String guardianEmail, BlindIndex index) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.course = Objects.requireNonNull(course, "course");
        changeGuardianEmail(guardianEmail, index);
    }

    public void changeGuardianEmail(String email, BlindIndex index) {
        guardianEmail = email;
        guardianEmailHash = index.of(email);
    }

    /** Paso de curso al cambiar de año. */
    public void moveTo(Course newCourse) {
        course = Objects.requireNonNull(newCourse, "course");
    }

    /** Dejó el colegio; la política de retención decide cuándo se borra (PRV-06). */
    public void deactivate() {
        active = false;
    }
}
