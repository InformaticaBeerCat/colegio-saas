package cl.colegiosaas.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;

/**
 * Analítica del sitio (REP-01).
 *
 * @param enabled   conteo propio y anónimo de visitas por página y día (sin cookies ni datos personales)
 * @param scriptUrl script de una herramienta externa (Plausible, Matomo…); se carga solo a quien aceptó la
 *                  analítica en el banner de cookies. Vacío = ninguna
 */
@ConfigurationProperties("app.analytics")
public record AnalyticsProperties(@DefaultValue("true") boolean enabled, @DefaultValue("") String scriptUrl) {

    /** Origen del script externo para la política de seguridad de contenido; nulo si no hay. */
    public String scriptOrigin() {
        if (scriptUrl == null || scriptUrl.isBlank()) {
            return null;
        }
        URI uri = URI.create(scriptUrl.strip());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("app.analytics.script-url debe ser una dirección https://");
        }
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    public boolean hasExternalScript() {
        return scriptOrigin() != null;
    }
}
