package cl.colegiosaas.platform;

/**
 * Módulos contratables por colegio (feature flags, CFG-07). Páginas, marca, documentos
 * obligatorios y privacidad no aparecen aquí: siempre están activos.
 */
public enum Feature {
    // Plan Base
    NEWS,
    CALENDAR,
    GALLERIES,
    CONTACT,
    /** Admisión vía SAE: página informativa + registro de interés + visitas. */
    SAE_ADMISSIONS,

    // Plan Comunidad
    SCHEDULING,
    EVENTS,
    NEWSLETTER,
    PUSH_NOTIFICATIONS,
    COMMUNITY_AREA,

    // Plan Admisión Pro
    /** Postulación propia (particulares pagados). */
    OWN_ADMISSIONS,
    PROSPECT_CRM,
    AUTOMATED_FOLLOW_UP,

    // Add-ons
    HOSTED_VIDEO,
    FAQ_CHATBOT,
    PAYMENTS,
    WHATSAPP_SMS,
    VIRTUAL_TOUR_360,
    FACE_DETECTION,
    MULTI_SCHOOL
}
