package cl.colegiosaas.news;

/** Flujo de aprobación de noticias (NOT-02). */
public enum NewsStatus {
    DRAFT,
    IN_REVIEW,
    /** Aprobada con fecha futura: se publica sola al llegar {@code publishAt}. */
    SCHEDULED,
    PUBLISHED,
    ARCHIVED
}
