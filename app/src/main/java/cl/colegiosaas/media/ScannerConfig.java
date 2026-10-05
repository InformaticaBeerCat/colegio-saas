package cl.colegiosaas.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class ScannerConfig {

    private static final Logger log = LoggerFactory.getLogger(ScannerConfig.class);

    /**
     * Con {@code APP_CLAMD_HOST}, ClamAV revisa cada archivo. Sin él, la subida solo valida tipo y tamaño:
     * aceptable en desarrollo; en producción el despliegue de la fase 9 incluye ClamAV.
     */
    @Bean
    FileScanner fileScanner(@Value("${app.antivirus.clamd-host:}") String host,
                            @Value("${app.antivirus.clamd-port:3310}") int port) {
        if (host.isBlank()) {
            log.warn("Sin antivirus (APP_CLAMD_HOST vacío): los archivos subidos solo se validan por tipo y tamaño");
            return new FileScanner() {
                @Override
                public Verdict scan(byte[] content) {
                    return Verdict.ok();
                }

                @Override
                public boolean isActive() {
                    return false;
                }
            };
        }
        log.info("Antivirus ClamAV en {}:{}", host, port);
        return new ClamAvScanner(host, port, Duration.ofSeconds(30));
    }
}
