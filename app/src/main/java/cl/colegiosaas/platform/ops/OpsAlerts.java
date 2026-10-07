package cl.colegiosaas.platform.ops;

import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.PlatformProperties;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.web.AppProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Alertas de operación (OPS-06): cada hora revisa la instalación y avisa por correo al proveedor y a quienes tienen
 * el permiso de plataforma lo que falla. Cada problema se avisa a lo más una vez al día, para no llenar la casilla.
 */
@Component
public class OpsAlerts {

    static final Duration REPEAT_AFTER = Duration.ofHours(24);

    private final OpsChecks checks;
    private final UpdateChecker updates;
    private final PlatformProperties properties;
    private final UserAccountRepository users;
    private final SchoolRepository schools;
    private final AppProperties app;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    OpsAlerts(OpsChecks checks, UpdateChecker updates, PlatformProperties properties, UserAccountRepository users,
              SchoolRepository schools, AppProperties app, ApplicationEventPublisher publisher, Clock clock) {
        this.checks = checks;
        this.updates = updates;
        this.properties = properties;
        this.users = users;
        this.schools = schools;
        this.app = app;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT1H")
    @Transactional(readOnly = true)
    public void scheduledRun() {
        run();
    }

    /** Revisa y avisa; devuelve los avisos enviados en esta pasada. */
    @Transactional(readOnly = true)
    public List<String> run() {
        Instant now = clock.instant();
        List<String> messages = checks.all().stream()
                .filter(OpsChecks.Check::failing)
                // El mismo problema (sin contar las cifras que cambian, como las horas) se avisa una vez al día.
                .filter(c -> due(c.key() + ":" + c.level() + ":" + c.detail().replaceAll("\\d+", "#"), now))
                .map(c -> (c.level() == OpsChecks.Level.PROBLEM ? "PROBLEMA" : "Aviso") + " — " + c.name() + ": " + c.detail())
                .collect(Collectors.toList());
        updates.available().filter(r -> due("update:" + r.version(), now))
                .ifPresent(r -> messages.add("Hay una versión nueva: " + r.version() + (r.url() == null ? "" : " (" + r.url() + ")")));
        if (messages.isEmpty()) {
            return messages;
        }
        String school = schools.findSingleton().map(School::getName).orElse("Instalación sin configurar");
        String body = """
                Revisión automática de %s (%s):

                %s

                Estado completo en %s
                """.formatted(school, app.baseUrl(), String.join("\n", messages), app.url("/admin/platform"));
        for (String to : recipients()) {
            publisher.publishEvent(new OutgoingMail(to, "[" + school + "] " + messages.size() + " alerta(s) de operación", body));
        }
        return messages;
    }

    private Set<String> recipients() {
        Set<String> recipients = new LinkedHashSet<>();
        if (!properties.alertsEmail().isBlank()) {
            recipients.add(properties.alertsEmail().strip());
        }
        users.findAllByOrderByNameAsc().stream()
                .filter(u -> u.canLogIn() && u.can(Permission.PLATFORM))
                .map(UserAccount::getEmail)
                .forEach(recipients::add);
        return recipients;
    }

    private boolean due(String key, Instant now) {
        Instant previous = lastSent.get(key);
        if (previous != null && previous.plus(REPEAT_AFTER).isAfter(now)) {
            return false;
        }
        lastSent.put(key, now);
        return true;
    }
}
