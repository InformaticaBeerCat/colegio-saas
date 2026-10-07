package cl.colegiosaas.platform.license;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.PlatformProperties;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.web.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Licencia de la instalación (OPS-05): la verifica con la llave pública del proveedor y limita los módulos que
 * se pueden activar al plan y add-ons contratados. Sin licencia (desarrollo) no hay límites; en producción es
 * obligatoria (lo exige {@code ProductionReadiness}).
 */
@Service
@EnableConfigurationProperties(PlatformProperties.class)
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    static final String PUBLIC_KEY_RESOURCE = "license/proveedor.pub";

    public enum State { NONE, VALID, IN_GRACE, EXPIRED, INVALID }

    /** Estado de la licencia para el panel y las alertas. */
    public record Status(State state, License license, String problem) {

        public boolean usable() {
            return state == State.VALID || state == State.IN_GRACE;
        }
    }

    private final PlatformProperties properties;
    private final SchoolRepository schools;
    private final SchoolTime time;
    private final AuditTrail audit;
    private final PublicKey publicKey;

    LicenseService(PlatformProperties properties, SchoolRepository schools, SchoolTime time, AuditTrail audit) {
        this.properties = properties;
        this.schools = schools;
        this.time = time;
        this.audit = audit;
        this.publicKey = LicenseCodec.publicKey(properties.licensePublicKey().isBlank()
                ? readResource() : properties.licensePublicKey());
    }

    public Status status() {
        if (properties.license().isBlank()) {
            return new Status(State.NONE, null, "La instalación no tiene licencia");
        }
        try {
            License license = LicenseCodec.verify(properties.license(), publicKey);
            LocalDate today = time.today();
            if (license.isBeyondGrace(today)) {
                return new Status(State.EXPIRED, license, "La licencia venció el " + license.expiresOn()
                        + ": solo quedan activos los módulos del plan Base");
            }
            if (license.isExpired(today)) {
                return new Status(State.IN_GRACE, license, "La licencia venció el " + license.expiresOn() + "; hay "
                        + License.GRACE_DAYS + " días de gracia para renovarla");
            }
            return new Status(State.VALID, license, null);
        } catch (LicenseCodec.InvalidLicense e) {
            return new Status(State.INVALID, null, e.getMessage());
        }
    }

    /** Plan que impone la licencia al instalar; vacío si no hay licencia usable. */
    public Optional<Plan> licensedPlan() {
        Status status = status();
        return status.usable() ? Optional.of(status.license().plan()) : Optional.empty();
    }

    /**
     * Módulos que se pueden activar. Sin licencia en desarrollo: todos. Con una licencia inválida o vencida
     * (pasada la gracia): solo los del plan Base, así el sitio sigue en línea.
     */
    public Set<Feature> allowedFeatures() {
        Status status = status();
        return switch (status.state()) {
            case VALID, IN_GRACE -> status.license().allowedFeatures();
            case NONE -> properties.isProduction() ? Plan.BASE.includedFeatures() : EnumSet.allOf(Feature.class);
            case EXPIRED, INVALID -> Plan.BASE.includedFeatures();
        };
    }

    /** Al arrancar y cada día: el colegio queda con el plan de la licencia y sin módulos fuera de ella. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 5 4 * * *")
    @Transactional
    public void enforce() {
        School school = schools.findSingleton().orElse(null);
        if (school == null) {
            return;
        }
        Status status = status();
        if (status.usable() && school.getPlan() != status.license().plan()) {
            school.changePlan(status.license().plan());
            audit.recordSystem(AuditAction.UPDATE, "School", school.getId(), "Plan según licencia: " + status.license().plan());
        }
        Set<Feature> allowed = allowedFeatures();
        for (Feature feature : EnumSet.allOf(Feature.class)) {
            if (school.hasFeature(feature) && !allowed.contains(feature)) {
                school.disableFeature(feature);
                audit.recordSystem(AuditAction.UPDATE, "School", school.getId(), "Módulo desactivado por licencia: " + feature);
            }
        }
        if (status.problem() != null) {
            log.warn("Licencia: {}", status.problem());
        }
    }

    /** El proveedor activa o desactiva un módulo, dentro de lo que la licencia permite. */
    @Transactional
    public void setFeature(Feature feature, boolean on) {
        School school = schools.findSingleton().orElseThrow();
        if (on && !allowedFeatures().contains(feature)) {
            throw new RuleViolation("La licencia no incluye ese módulo");
        }
        if (on) {
            school.enableFeature(feature);
        } else {
            school.disableFeature(feature);
        }
        audit.record(AuditAction.UPDATE, "School", school.getId(), (on ? "Módulo activado: " : "Módulo desactivado: ") + feature);
    }

    private static String readResource() {
        try {
            return new String(new ClassPathResource(PUBLIC_KEY_RESOURCE).getContentAsByteArray(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Falta la llave pública de licencias (" + PUBLIC_KEY_RESOURCE + ")", e);
        }
    }
}
