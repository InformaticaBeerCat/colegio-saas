package cl.colegiosaas.site;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Acceso rápido a plataformas que el colegio ya usa: Napsis, Classroom, pago de mensualidad… (PUB-11). */
@Entity
@Table(name = "quick_link")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuickLink extends BaseEntity {

    @NotBlank
    private String label;

    @NotBlank
    private String url;

    /** Nombre del ícono dentro del set de íconos del tema. */
    private String icon;

    private int sortOrder;

    private boolean active = true;

    public QuickLink(String label, String url, int sortOrder) {
        this.label = label;
        this.url = url;
        this.sortOrder = sortOrder;
    }
}
