package cl.colegiosaas.consent;

/** Cómo llegó la autorización: queda como parte de la evidencia. */
public enum ConsentMethod {
    PAPER_FORM,
    /** El apoderado la dio en la zona comunidad (ZON-03, v2). */
    COMMUNITY_AREA,
    EMAIL
}
