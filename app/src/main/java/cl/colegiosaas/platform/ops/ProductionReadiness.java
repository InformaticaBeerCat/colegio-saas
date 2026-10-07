package cl.colegiosaas.platform.ops;

import cl.colegiosaas.platform.PlatformProperties;
import cl.colegiosaas.platform.license.LicenseService;
import cl.colegiosaas.shared.crypto.CryptoProperties;
import cl.colegiosaas.shared.web.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * En producción ({@code APP_ENVIRONMENT=production}) la aplicación no arranca con una configuración insegura: llaves
 * de desarrollo, sitio sin HTTPS, cookies sin {@code Secure} o sin licencia válida para su dominio (OPS-01, OPS-05).
 * Lo recomendable pero no indispensable (correo, antivirus) solo se avisa en el log.
 */
@Component
public class ProductionReadiness {

    private static final Logger log = LoggerFactory.getLogger(ProductionReadiness.class);

    /** Llaves de application.yml: sirven para desarrollo y tests, nunca para datos reales. */
    static final List<String> DEVELOPMENT_KEYS = List.of(
            "uZVrGeLHRbUrhdmF3EycAPb6SvRvwKdsZFCp8d0ZmH4=",
            "o8bfT9FdOu0BbSJmCBc15RAXiiNLjGv12HrxTX0NIR8=");

    private final PlatformProperties platform;
    private final CryptoProperties crypto;
    private final AppProperties app;
    private final LicenseService licenses;
    private final Environment environment;

    ProductionReadiness(PlatformProperties platform, CryptoProperties crypto, AppProperties app, LicenseService licenses,
                        Environment environment) {
        this.platform = platform;
        this.crypto = crypto;
        this.app = app;
        this.licenses = licenses;
        this.environment = environment;
    }

    @PostConstruct
    void check() {
        if (!platform.isProduction()) {
            return;
        }
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("La configuración no es apta para producción:\n- " + String.join("\n- ", problems));
        }
        warnings().forEach(w -> log.warn("Producción: {}", w));
    }

    /** Lo que impide arrancar en producción. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (DEVELOPMENT_KEYS.contains(crypto.fieldKey()) || DEVELOPMENT_KEYS.contains(crypto.indexKey())) {
            problems.add("APP_FIELD_KEY y APP_INDEX_KEY usan las llaves de desarrollo: genera unas propias (openssl rand -base64 32)");
        }
        if (crypto.fieldKey() != null && crypto.fieldKey().equals(crypto.indexKey())) {
            problems.add("APP_FIELD_KEY y APP_INDEX_KEY deben ser distintas");
        }
        URI base = URI.create(app.baseUrl());
        if (!"https".equals(base.getScheme())) {
            problems.add("APP_BASE_URL debe ser https://");
        }
        if (!environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)) {
            problems.add("APP_SECURE_COOKIES debe ser true");
        }
        if (!platform.licensePublicKey().isBlank()) {
            problems.add("app.platform.license-public-key solo se permite fuera de producción");
        }
        LicenseService.Status license = licenses.status();
        if (license.state() == LicenseService.State.NONE || license.state() == LicenseService.State.INVALID) {
            problems.add("APP_LICENSE: " + license.problem());
        } else if (base.getHost() != null && !sameDomain(base.getHost(), license.license().domain())) {
            problems.add("La licencia es para " + license.license().domain() + " y el sitio está en " + base.getHost());
        }
        return problems;
    }

    /** Lo recomendable: se avisa, pero no impide arrancar. */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        if (blank(environment.getProperty("spring.mail.host"))) {
            warnings.add("Sin SMTP (SPRING_MAIL_HOST): los correos solo quedan en el log");
        }
        if (blank(environment.getProperty("app.antivirus.clamd-host"))) {
            warnings.add("Sin antivirus (APP_CLAMD_HOST): los archivos solo se revisan por tipo y tamaño");
        }
        if (blank(platform.backupStatusFile())) {
            warnings.add("Sin estado de respaldos (APP_BACKUP_STATUS_FILE): no se puede alertar si el respaldo falla");
        }
        if (blank(platform.alertsEmail())) {
            warnings.add("Sin correo de alertas para el proveedor (APP_ALERTS_EMAIL)");
        }
        return warnings;
    }

    static boolean sameDomain(String host, String licensed) {
        String h = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        return h.equals(licensed.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
