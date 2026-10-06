package cl.colegiosaas.privacy;

import jakarta.servlet.http.HttpServletRequest;

/** Desde dónde se otorgó un consentimiento: evidencia para PRV-04. */
public record RequestOrigin(String source, String ipAddress, String userAgent) {

    /** El formulario (su ruta), la IP y el navegador de la petición, recortados al largo de las columnas. */
    public static RequestOrigin of(HttpServletRequest request) {
        return new RequestOrigin(cut(request.getRequestURI(), 300), request.getRemoteAddr(),
                cut(request.getHeader("User-Agent"), 300));
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
