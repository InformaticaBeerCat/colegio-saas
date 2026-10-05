package cl.colegiosaas.identity;

/**
 * Permisos finos del panel (USR-01). Los roles son paquetes de permisos ({@link Role#permissions()});
 * el código pregunta siempre por permisos, nunca por roles: así cambiar qué puede hacer un rol
 * es tocar un solo lugar.
 */
public enum Permission {
    /** Entrar al panel. Apoderados y estudiantes no lo tienen (su zona es la comunidad, v2). */
    PANEL_ACCESS,

    // Sitio y contenido
    SITE_DESIGN,
    PAGES,
    NEWS_EDIT,
    /** Aprobar y publicar noticias (NOT-02). */
    NEWS_PUBLISH,
    ANNOUNCEMENTS,
    CALENDAR,
    GENERAL_INFO,
    DOCUMENTS,

    // Medios y autorización de imagen
    MEDIA_UPLOAD,
    /** Aprobar o rechazar fotos según las autorizaciones (MED-06). */
    MEDIA_REVIEW,
    STUDENTS_AND_CONSENTS,

    // Interacción
    INQUIRIES,
    /** Su propia agenda y disponibilidad (AGE-10). */
    SCHEDULING_OWN,
    SCHEDULING_ALL,
    EVENTS,
    ADMISSIONS,

    // Privacidad y administración
    PRIVACY,
    USERS,
    SCHOOL_SETTINGS,
    AUDIT_LOG,

    /** Solo el proveedor: plan y módulos, CSS personalizado, actualizaciones (CFG-07, CFG-09). */
    PLATFORM,

    // Secciones satélite (PUB-07)
    SECTION_PARENTS_CENTER,
    SECTION_STUDENT_COUNCIL
}
