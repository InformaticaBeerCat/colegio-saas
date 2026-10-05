package cl.colegiosaas.page;

/**
 * Páginas que el sitio necesita encontrar por su tipo (la home, la de convivencia…).
 * Cada tipo salvo CUSTOM existe a lo más una vez; lo controla el servicio de páginas (fase 3).
 */
public enum PageKind {
    HOME,
    ABOUT,
    /** Proyecto Educativo Institucional (PUB-02). */
    PEI,
    /** Niveles y oferta educativa (PUB-03). */
    LEVELS,
    /** Infraestructura (PUB-05). */
    FACILITIES,
    /** Convivencia escolar: equipo, protocolos y canal de denuncia (DOC-07). */
    COEXISTENCE,
    CUSTOM
}
