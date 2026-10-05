package cl.colegiosaas.scheduling;

/** Quién puede reservar un tipo de cita. */
public enum AppointmentAudience {
    /** Familias postulantes: visitas guiadas. */
    PROSPECTIVE_FAMILY,
    /** Apoderados: entrevistas con profesor jefe, dirección, convivencia. */
    GUARDIAN,
    ANYONE
}
