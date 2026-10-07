package cl.colegiosaas.platform.ops;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

/**
 * Las revisiones de la instalación en {@code /actuator/health} (OPS-06), para el monitoreo externo. El contenedor
 * solo se reinicia por {@code /actuator/health/liveness}: un respaldo atrasado alerta, pero no reinicia el sitio.
 */
@Configuration(proxyBeanMethods = false)
class OpsHealthIndicators {

    @Bean
    HealthIndicator backupsHealthIndicator(OpsChecks checks) {
        return indicator(checks::backups);
    }

    @Bean
    HealthIndicator antivirusHealthIndicator(OpsChecks checks) {
        return indicator(checks::antivirus);
    }

    @Bean
    HealthIndicator storageHealthIndicator(OpsChecks checks) {
        return indicator(checks::storage);
    }

    @Bean
    HealthIndicator licenseHealthIndicator(OpsChecks checks) {
        return indicator(checks::license);
    }

    private static HealthIndicator indicator(Supplier<OpsChecks.Check> check) {
        return () -> {
            OpsChecks.Check result = check.get();
            Health.Builder health = result.level() == OpsChecks.Level.PROBLEM ? Health.down() : Health.up();
            return health.withDetail("estado", result.level().name()).withDetail("detalle", result.detail()).build();
        };
    }
}
