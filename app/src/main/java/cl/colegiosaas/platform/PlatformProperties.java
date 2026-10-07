package cl.colegiosaas.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Operación de la instalación (fase 9).
 *
 * @param environment      {@code development} o {@code production}; en producción la aplicación se niega a arrancar
 *                         con una configuración insegura
 * @param license          licencia firmada por el proveedor (OPS-05)
 * @param licensePublicKey llave pública alternativa para verificar licencias; solo fuera de producción (tests)
 * @param updatesFeedUrl   dónde consultar si hay una versión nueva (OPS-03); vacío = no se consulta
 * @param alertsEmail      correo del proveedor que recibe las alertas de la instalación (OPS-06)
 * @param backupStatusFile archivo que escribe el respaldo diario con su resultado (SEG-05)
 * @param backupMaxAge     antigüedad máxima aceptable del último respaldo exitoso
 */
@ConfigurationProperties("app.platform")
public record PlatformProperties(@DefaultValue("development") String environment,
                                 @DefaultValue("") String license,
                                 @DefaultValue("") String licensePublicKey,
                                 @DefaultValue("") String updatesFeedUrl,
                                 @DefaultValue("") String alertsEmail,
                                 @DefaultValue("") String backupStatusFile,
                                 @DefaultValue("26h") Duration backupMaxAge) {

    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment);
    }
}
