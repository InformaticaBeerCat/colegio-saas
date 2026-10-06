package cl.colegiosaas.privacy;

/** Finalidad de un consentimiento. Cada una es una casilla separada y no premarcada (PRV-02). */
public enum ConsentPurpose {
    CONTACT(LegalTextKind.NOTICE_CONTACT, false),
    SCHEDULING(LegalTextKind.NOTICE_SCHEDULING, false),
    EVENT_REGISTRATION(LegalTextKind.NOTICE_EVENTS, false),
    ADMISSIONS(LegalTextKind.NOTICE_ADMISSIONS, false),
    /** Correos de seguimiento a postulantes (ADM-06): aparte del registro de interés. */
    ADMISSIONS_FOLLOW_UP(LegalTextKind.NOTICE_ADMISSIONS, true),
    NEWSLETTER(LegalTextKind.NOTICE_NEWSLETTER, true),
    /** Analítica solo tras aceptar en el banner de cookies (PRV-03). */
    ANALYTICS_COOKIES(LegalTextKind.COOKIE_POLICY, true),
    DATA_REQUEST(LegalTextKind.NOTICE_DATA_REQUESTS, false);

    private final LegalTextKind notice;
    private final boolean ongoing;

    ConsentPurpose(LegalTextKind notice, boolean ongoing) {
        this.notice = notice;
        this.ongoing = ongoing;
    }

    /** Texto sobre el que se consiente: el aviso del formulario donde aparece la casilla (PRV-01). */
    public LegalTextKind notice() {
        return notice;
    }

    /**
     * Consentimiento que sigue produciendo efectos mientras no se retire (boletín, seguimiento). Los de una
     * sola vez (enviar un mensaje, reservar una cita) se pueden anonimizar al vencer su plazo (PRV-06).
     */
    public boolean isOngoing() {
        return ongoing;
    }
}
