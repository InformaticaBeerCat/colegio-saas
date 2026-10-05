package cl.colegiosaas.privacy;

public enum RetentionAction {
    DELETE,
    /** Conserva la fila para estadísticas pero borra los datos que identifican a la persona. */
    ANONYMIZE
}
