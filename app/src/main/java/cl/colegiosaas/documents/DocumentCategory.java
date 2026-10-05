package cl.colegiosaas.documents;

/** Categorías predefinidas de documentos institucionales (DOC-01). */
public enum DocumentCategory {
    /** Reglamento Interno. */
    INTERNAL_REGULATIONS,
    /** Protocolo de actuación (parte del Reglamento Interno). */
    PROTOCOL,
    /** Anexo del Reglamento Interno. */
    ANNEX,
    /** Reglamento de Evaluación. */
    EVALUATION_REGULATIONS,
    /** Plan de Gestión de la Convivencia Escolar. */
    COEXISTENCE_PLAN,
    /** Plan Integral de Seguridad Escolar (PISE). */
    SCHOOL_SAFETY_PLAN,
    INCLUSION_PLAN,
    /** Plan de Formación Ciudadana. */
    CITIZENSHIP_PLAN,
    /** Cuenta pública anual. */
    PUBLIC_ACCOUNT,
    /** Proyecto Educativo Institucional. */
    PEI,
    OTHER;

    /**
     * REX N°781/2025: el Reglamento Interno con sus protocolos y anexos debe publicarse indicando
     * año académico, nombre del establecimiento, RBD y fecha de última actualización (DOC-02).
     */
    public boolean requiresRegulatoryMetadata() {
        return this == INTERNAL_REGULATIONS || this == PROTOCOL || this == ANNEX;
    }
}
