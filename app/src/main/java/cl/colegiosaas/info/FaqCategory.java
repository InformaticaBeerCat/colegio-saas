package cl.colegiosaas.info;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Categoría de preguntas frecuentes: admisión, uniforme, útiles, horarios… (PUB-09). */
@Entity
@Table(name = "faq_category")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqCategory extends BaseEntity {

    @NotBlank
    private String name;

    private int sortOrder;

    public FaqCategory(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
