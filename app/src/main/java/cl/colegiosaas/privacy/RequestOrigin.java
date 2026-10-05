package cl.colegiosaas.privacy;

/** Desde dónde se otorgó un consentimiento: evidencia para PRV-04. */
public record RequestOrigin(String source, String ipAddress, String userAgent) {
}
