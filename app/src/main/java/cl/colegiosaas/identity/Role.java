package cl.colegiosaas.identity;

/**
 * Roles dentro de un colegio (sección 2 de requerimientos). El Super Admin no está aquí:
 * es un {@code PlatformOperator}. Los permisos finos por rol se definen en la fase 2.
 */
public enum Role {
    SCHOOL_ADMIN(true),
    EDITOR(false),
    /** Gestor de agenda: admisión, inspectoría, profesores jefe. */
    SCHEDULE_MANAGER(false),
    /** Gestor de consentimientos: autorizaciones de imagen y revisión de fotos. */
    CONSENT_MANAGER(true),
    /** Editor satélite del Centro de Padres. */
    PARENTS_CENTER_EDITOR(false),
    /** Editor satélite del Centro de Estudiantes. */
    STUDENT_COUNCIL_EDITOR(false),
    /** Apoderado. */
    GUARDIAN(false),
    STUDENT(false);

    private final boolean requiresMfa;

    Role(boolean requiresMfa) {
        this.requiresMfa = requiresMfa;
    }

    /** USR-02: MFA obligatorio para administradores y para quien ve datos de menores. */
    public boolean requiresMfa() {
        return requiresMfa;
    }
}
