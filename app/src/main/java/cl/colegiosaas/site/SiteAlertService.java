package cl.colegiosaas.site;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.shared.web.SafeUrls;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Banner de alerta en todo el sitio (PUB-12): suspensión de clases, emergencia, simulacro.
 * Se activa en un clic y puede apagarse sola al terminar su ventana.
 */
@Service
public class SiteAlertService {

    private final SiteAlertRepository alerts;
    private final AuditTrail audit;
    private final Clock clock;

    SiteAlertService(SiteAlertRepository alerts, AuditTrail audit, Clock clock) {
        this.alerts = alerts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SiteAlert> list() {
        return alerts.findAll().stream().sorted(Comparator.comparing(SiteAlert::getCreatedAt).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<SiteAlert> visibleNow() {
        return alerts.findVisibleAt(clock.instant());
    }

    /**
     * Crea la alerta; con {@code activateNow} queda visible de inmediato (lo normal en una emergencia).
     *
     * @param startsAt nulo = desde que se activa
     * @param endsAt   nulo = hasta que alguien la desactive
     */
    @Transactional
    public SiteAlert create(String message, AlertSeverity severity, String linkUrl, String linkLabel,
                            Instant startsAt, Instant endsAt, boolean activateNow) {
        if (message == null || message.isBlank() || message.strip().length() > 300) {
            throw new RuleViolation("El aviso necesita un texto de hasta 300 caracteres");
        }
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new RuleViolation("El aviso no puede terminar antes de empezar");
        }
        String link = linkUrl == null || linkUrl.isBlank() ? null : linkUrl.strip();
        if (link != null && !SafeUrls.isAllowed(link)) {
            throw new RuleViolation("Enlace no permitido: usa una ruta del sitio (/comunicados) o una dirección https://");
        }
        SiteAlert alert = new SiteAlert(message.strip(), severity == null ? AlertSeverity.INFO : severity);
        alert.setLinkUrl(link);
        alert.setLinkLabel(link == null || linkLabel == null || linkLabel.isBlank() ? null : linkLabel.strip());
        alert.setStartsAt(startsAt);
        alert.setEndsAt(endsAt);
        if (activateNow) {
            alert.activate();
        }
        alerts.save(alert);
        audit.record(AuditAction.CREATE, "SiteAlert", alert.getId(), alert.getSeverity() + ": " + alert.getMessage());
        return alert;
    }

    @Transactional
    public void activate(long id, boolean active) {
        SiteAlert alert = alerts.findById(id).orElseThrow(() -> new NotFound("El aviso no existe"));
        if (active) {
            alert.activate();
        } else {
            alert.deactivate();
        }
        audit.record(active ? AuditAction.PUBLISH : AuditAction.UNPUBLISH, "SiteAlert", id, alert.getMessage());
    }

    @Transactional
    public void delete(long id) {
        SiteAlert alert = alerts.findById(id).orElseThrow(() -> new NotFound("El aviso no existe"));
        alerts.delete(alert);
        audit.record(AuditAction.DELETE, "SiteAlert", id, alert.getMessage());
    }
}
