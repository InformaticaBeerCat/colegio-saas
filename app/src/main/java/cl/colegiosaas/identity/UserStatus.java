package cl.colegiosaas.identity;

public enum UserStatus {
    /** Creado por el admin; aún no acepta la invitación ni fija su contraseña. */
    INVITED,
    ACTIVE,
    /** Suspendido por un administrador. El bloqueo por intentos fallidos es temporal y va aparte. */
    LOCKED,
    /** Ya no trabaja en el colegio (USR-04). Se conserva por la auditoría. */
    DEACTIVATED
}
