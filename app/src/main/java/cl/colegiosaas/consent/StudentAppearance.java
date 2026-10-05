package cl.colegiosaas.consent;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "Este estudiante aparece en esta foto": etiquetado manual que hace el gestor al revisar,
 * sin reconocimiento facial. Sirve para encontrar y retirar todas sus fotos si se revoca
 * la autorización (MED-09). Vive aquí y no en media para que media no dependa de estudiantes.
 */
@Entity
@Table(name = "student_appearance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAppearance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private MediaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount taggedBy;

    public StudentAppearance(Student student, MediaAsset asset, UserAccount taggedBy) {
        this.student = student;
        this.asset = asset;
        this.taggedBy = taggedBy;
    }
}
