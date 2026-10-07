package cl.colegiosaas.shared.forms;

import cl.colegiosaas.shared.crypto.BlindIndex;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Antispam sin captcha ni servicios externos (COM-08), para todos los formularios públicos:
 * <ul>
 *     <li>campo trampa invisible ({@value #HONEYPOT}): una persona no lo ve, un bot lo llena;</li>
 *     <li>sello firmado con la hora en que se mostró el formulario ({@value #STAMP}): enviarlo en menos de
 *     unos segundos delata a un bot, y sin sesión (el sitio público no la abre);</li>
 *     <li>límite de envíos por IP y formulario, en memoria (una instancia por colegio).</li>
 * </ul>
 * A un bot se le responde como si todo hubiera salido bien, para no enseñarle qué lo delató.
 */
@Component
@EnableConfigurationProperties(FormGuardProperties.class)
public class FormGuard {

    public static final String HONEYPOT = "sitio_web";
    public static final String STAMP = "sello";

    public enum Verdict {
        OK,
        /** Bot: se finge éxito y no se guarda nada. */
        BOT,
        /** Formulario abierto hace demasiado: se pide volver a cargarlo. */
        EXPIRED,
        /** Demasiados envíos desde la misma IP. */
        TOO_MANY
    }

    private final FormGuardProperties properties;
    private final BlindIndex signer;
    private final Clock clock;
    private final Map<String, Deque<Instant>> submissions = new ConcurrentHashMap<>();

    FormGuard(FormGuardProperties properties, BlindIndex signer, Clock clock) {
        this.properties = properties;
        this.signer = signer;
        this.clock = clock;
    }

    /** Sello para el campo oculto del formulario. */
    public String stamp() {
        long seconds = clock.instant().getEpochSecond();
        return seconds + "." + sign(seconds);
    }

    /**
     * Revisa un envío. Si es válido, lo cuenta para el límite por IP.
     *
     * @param form nombre del formulario ("contacto", "cita"…): cada uno tiene su propio límite
     */
    public Verdict check(String form, String ip, String honeypot, String stamp) {
        if (honeypot != null && !honeypot.isBlank()) {
            return Verdict.BOT;
        }
        Instant shownAt = shownAt(stamp);
        if (shownAt == null) {
            return Verdict.BOT;
        }
        Instant now = clock.instant();
        if (now.isBefore(shownAt.plus(properties.minFillTime()))) {
            return Verdict.BOT;
        }
        if (now.isAfter(shownAt.plus(properties.maxAge()))) {
            return Verdict.EXPIRED;
        }
        String key = form + "|" + (ip == null ? "?" : ip);
        Deque<Instant> recent = submissions.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            Instant limit = now.minus(properties.ipWindow());
            while (!recent.isEmpty() && recent.peekFirst().isBefore(limit)) {
                recent.removeFirst();
            }
            if (recent.size() >= properties.maxPerIp()) {
                return Verdict.TOO_MANY;
            }
            recent.addLast(now);
        }
        return Verdict.OK;
    }

    private Instant shownAt(String stamp) {
        if (stamp == null) {
            return null;
        }
        int dot = stamp.indexOf('.');
        if (dot < 1) {
            return null;
        }
        try {
            long seconds = Long.parseLong(stamp.substring(0, dot));
            return sign(seconds).equals(stamp.substring(dot + 1)) ? Instant.ofEpochSecond(seconds) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String sign(long seconds) {
        return signer.of("formulario:" + seconds).substring(0, 24);
    }
}
