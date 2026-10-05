package cl.colegiosaas.contact;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Área que recibe consultas: admisión, secretaría, convivencia, finanzas (COM-01).
 * También aparece en la ficha del colegio como "correos por área" (PUB-04).
 */
@Entity
@Table(name = "contact_area")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactArea extends BaseEntity {

    @NotBlank
    private String name;

    /** Casilla del área: recibe el aviso de cada consulta nueva. */
    @Email
    @NotBlank
    private String notifyEmail;

    private String description;

    private boolean active = true;

    private int sortOrder;

    public ContactArea(String name, String notifyEmail, int sortOrder) {
        this.name = name;
        this.notifyEmail = notifyEmail;
        this.sortOrder = sortOrder;
    }
}
