package cl.colegiosaas.admissions;

/** Modo de admisión (ADM-01). */
public enum AdmissionMode {
    /** Municipales, SLEP y particulares subvencionados: se postula por el SAE del Mineduc. */
    SAE,
    /** Particulares pagados: proceso propio del colegio (formulario de postulación en v2). */
    OWN
}
