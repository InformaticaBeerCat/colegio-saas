package cl.colegiosaas.privacy;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tipos de datos personales con plazo de conservación propio (PRV-06). No todos admiten anonimizar:
 * una cita o un estudiante sin datos que identifiquen no sirve para nada, se borra.
 */
public enum RetentionCategory {
    INQUIRIES(EnumSet.allOf(RetentionAction.class)),
    PROSPECTS(EnumSet.of(RetentionAction.DELETE)),
    APPOINTMENTS(EnumSet.of(RetentionAction.DELETE)),
    EVENT_REGISTRATIONS(EnumSet.of(RetentionAction.DELETE)),
    DATA_SUBJECT_REQUESTS(EnumSet.of(RetentionAction.ANONYMIZE)),
    /** Otros registros apuntan a los consentimientos: se anonimizan, nunca se borran. */
    CONSENT_RECORDS(EnumSet.of(RetentionAction.ANONYMIZE)),
    STUDENTS(EnumSet.of(RetentionAction.DELETE)),
    AUDIT_LOG(EnumSet.of(RetentionAction.DELETE));

    private final Set<RetentionAction> allowedActions;

    RetentionCategory(EnumSet<RetentionAction> allowedActions) {
        this.allowedActions = allowedActions;
    }

    public Set<RetentionAction> allowedActions() {
        return allowedActions;
    }
}
