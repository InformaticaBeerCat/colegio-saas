package cl.colegiosaas.site;

/**
 * Sección del sitio a la que pertenece un contenido. Los editores satélite (PUB-07)
 * solo pueden publicar en la suya.
 */
public enum SiteSection {
    MAIN,
    /** Centro de Padres. */
    PARENTS_CENTER,
    /** Centro de Estudiantes. */
    STUDENT_COUNCIL,
    /** Exalumnos (PUB-08). */
    ALUMNI
}
