package cl.colegiosaas.audit;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    PUBLISH,
    UNPUBLISH,
    APPROVE,
    REJECT,
    /** Acceso a datos personales (consultas, postulantes, autorizaciones de imagen). */
    VIEW_PERSONAL_DATA,
    EXPORT,
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    ACCOUNT_LOCKED,
    PERMISSIONS_CHANGED,
    INVITE,
    DEACTIVATE,
    PASSWORD_CHANGED,
    MFA_ENABLED,
    MFA_RESET,
    SETUP_COMPLETED
}
