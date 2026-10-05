package cl.colegiosaas.privacy;

/**
 * Textos legales versionados (DOC-06, PRV-01). Cada formulario tiene su propio aviso de tratamiento
 * (finalidad, responsable, plazo de conservación, derechos).
 */
public enum LegalTextKind {
    PRIVACY_POLICY,
    COOKIE_POLICY,
    TERMS,
    /** Formulario de autorización de uso de imagen que firman los apoderados. */
    IMAGE_CONSENT_FORM,
    NOTICE_CONTACT,
    NOTICE_SCHEDULING,
    NOTICE_EVENTS,
    NOTICE_ADMISSIONS,
    NOTICE_NEWSLETTER,
    NOTICE_DATA_REQUESTS
}
