package cl.colegiosaas.media;

/** Quién puede ver un álbum (MED-05). Todo lo que no es PUBLIC lleva noindex (SEO-03). */
public enum AlbumVisibility {
    PUBLIC,
    /** Solo apoderados y estudiantes con sesión (zona comunidad, v2). */
    COMMUNITY,
    /** Solo las familias de un curso. */
    COURSE
}
