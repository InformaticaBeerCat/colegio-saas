package cl.colegiosaas.media;

/** Revisión de autorización de imagen por el gestor de consentimientos (MED-06). */
public enum ReviewStatus {
    /** Documentos, o imágenes sin personas que el gestor eximió (logo, fachada). */
    NOT_REQUIRED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
