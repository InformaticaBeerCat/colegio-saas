package cl.colegiosaas.platform;

public enum SchoolStatus {
    /** Recién creado; el sitio aún no es público (onboarding). */
    ONBOARDING,
    ACTIVE,
    /** Sitio fuera de línea temporalmente (p. ej., licencia vencida). */
    SUSPENDED,
    /** Contrato terminado; datos pendientes de exportación y borrado (OPS-10). */
    TERMINATED
}
