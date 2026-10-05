package cl.colegiosaas.info;

import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Taller o actividad extraprogramática (PUB-06). La inscripción con autorización llega en v2 (EVE-04). */
@Entity
@Table(name = "workshop")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workshop extends BaseEntity {

    @NotBlank
    private String name;

    @Size(max = 2000)
    private String description;

    /** Texto libre: "Martes y jueves, 15:30 a 17:00". */
    private String schedule;

    /** Nombre del profesor o monitor a cargo. */
    private String instructor;

    private Integer capacity;

    @ManyToMany
    @JoinTable(name = "workshop_grade_level",
            joinColumns = @JoinColumn(name = "workshop_id"),
            inverseJoinColumns = @JoinColumn(name = "grade_level_id"))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<GradeLevel> gradeLevels = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    private MediaAsset image;

    private int academicYear;

    private boolean active = true;

    public Workshop(String name, int academicYear) {
        this.name = name;
        this.academicYear = academicYear;
    }

    public void targetGradeLevels(Set<GradeLevel> levels) {
        gradeLevels.clear();
        gradeLevels.addAll(levels);
    }

    public Set<GradeLevel> getGradeLevels() {
        return Collections.unmodifiableSet(gradeLevels);
    }
}
