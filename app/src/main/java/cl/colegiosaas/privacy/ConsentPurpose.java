package cl.colegiosaas.privacy;

/** Finalidad de un consentimiento. Cada una es una casilla separada y no premarcada (PRV-02). */
public enum ConsentPurpose {
    CONTACT,
    SCHEDULING,
    EVENT_REGISTRATION,
    ADMISSIONS,
    /** Correos de seguimiento a postulantes (ADM-06): aparte del registro de interés. */
    ADMISSIONS_FOLLOW_UP,
    NEWSLETTER,
    /** Analítica solo tras aceptar en el banner de cookies (PRV-03). */
    ANALYTICS_COOKIES,
    DATA_REQUEST
}
