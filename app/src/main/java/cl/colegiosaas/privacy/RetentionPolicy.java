package cl.colegiosaas.privacy;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plazo de conservación por tipo de dato (PRV-06). Una tarea diaria (fase 6) borra o anonimiza
 * lo vencido. Los valores iniciales vienen en la migración V5 y se ajustan desde el panel.
 */
@Entity
@Table(name = "retention_policy")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetentionPolicy extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private RetentionCategory dataCategory;

    @Positive
    private int retentionDays;

    @Enumerated(EnumType.STRING)
    private RetentionAction action;

    public RetentionPolicy(RetentionCategory dataCategory, int retentionDays, RetentionAction action) {
        this.dataCategory = dataCategory;
        this.retentionDays = retentionDays;
        this.action = action;
    }
}
