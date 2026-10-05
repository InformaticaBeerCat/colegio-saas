package cl.colegiosaas.calendar;

/** Tipo de entrada del calendario escolar (NOT-04); el tema le asigna color e ícono. */
public enum EventKind {
    HOLIDAY,
    VACATION,
    /** Reunión de apoderados por curso (AGE-08). */
    PARENT_MEETING,
    /** Acto, ceremonia, licenciatura. */
    CEREMONY,
    /** Jornada de puertas abiertas para postulantes (ADM-03). */
    OPEN_HOUSE,
    ACADEMIC,
    SPORTS,
    CULTURAL,
    OTHER
}
