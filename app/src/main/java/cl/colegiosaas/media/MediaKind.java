package cl.colegiosaas.media;

public enum MediaKind {
    IMAGE,
    /** Video alojado en el servidor (add-on HOSTED_VIDEO). */
    VIDEO,
    /** Video de YouTube o Vimeo: no hay archivo propio, solo la URL. */
    EMBEDDED_VIDEO,
    DOCUMENT;

    /** Fotos y videos pueden mostrar estudiantes: pasan por revisión antes de publicarse (MED-06). */
    public boolean requiresReview() {
        return this != DOCUMENT;
    }
}
