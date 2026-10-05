package cl.colegiosaas.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la instalación que no es del colegio sino del despliegue.
 *
 * @param baseUrl  URL pública del sitio, para armar enlaces en los correos (sin "/" final)
 * @param mailFrom remitente de los correos del sistema
 */
@ConfigurationProperties("app")
public record AppProperties(String baseUrl, String mailFrom) {

    public String url(String path) {
        return baseUrl + path;
    }
}
