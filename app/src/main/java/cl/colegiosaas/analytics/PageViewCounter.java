package cl.colegiosaas.analytics;

import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.web.AppProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/**
 * Conteo anónimo de visitas (REP-01): en memoria por página y día, y a la base cada minuto como totales. No guarda
 * IP, navegador ni cookies, y respeta "No rastrear" y "Control global de privacidad" del navegador.
 */
@Component
@EnableConfigurationProperties(AnalyticsProperties.class)
public class PageViewCounter {

    private static final Pattern BOT = Pattern.compile("bot|crawl|spider|slurp|preview|lighthouse|headless|monitor|curl|wget|python|java/",
            Pattern.CASE_INSENSITIVE);

    record Key(LocalDate day, String value) {
    }

    private final Map<Key, LongAdder> views = new ConcurrentHashMap<>();
    private final Map<Key, LongAdder> referrers = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final SchoolTime time;
    private final String ownHost;

    PageViewCounter(JdbcTemplate jdbc, SchoolTime time, AppProperties app) {
        this.jdbc = jdbc;
        this.time = time;
        this.ownHost = host(app.baseUrl());
    }

    /**
     * Cuenta una página vista.
     *
     * @param referer cabecera Referer: solo se guarda el dominio, y solo si es otro sitio
     */
    public void record(String path, String userAgent, String referer, boolean doNotTrack) {
        if (doNotTrack || userAgent == null || BOT.matcher(userAgent).find()) {
            return;
        }
        LocalDate day = time.today();
        String cleanPath = path.length() > 300 ? path.substring(0, 300) : path;
        views.computeIfAbsent(new Key(day, cleanPath), k -> new LongAdder()).increment();
        String source = host(referer);
        if (source != null && !source.equals(ownHost)) {
            referrers.computeIfAbsent(new Key(day, source.length() > 100 ? source.substring(0, 100) : source),
                    k -> new LongAdder()).increment();
        }
    }

    /** Pasa a la base lo contado. Corre cada minuto y al apagar la aplicación. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @PreDestroy
    @Transactional
    public void flush() {
        drain(views, "page_view_daily", "path", "views");
        drain(referrers, "referrer_daily", "source", "visits");
    }

    private void drain(Map<Key, LongAdder> counters, String table, String column, String counter) {
        LocalDate today = time.today();
        for (Map.Entry<Key, LongAdder> entry : counters.entrySet()) {
            Key key = entry.getKey();
            // Se resetea en vez de quitar: una visita que llega justo ahora queda para la próxima pasada.
            long count = entry.getValue().sumThenReset();
            if (key.day().isBefore(today)) {
                counters.remove(key, entry.getValue());
            }
            if (count == 0) {
                continue;
            }
            // MySQL y H2 en modo MySQL: suma si la fila del día ya existe.
            jdbc.update("insert into " + table + " (view_date, " + column + ", " + counter + ") values (?, ?, ?) "
                    + "on duplicate key update " + counter + " = " + counter + " + ?", key.day(), key.value(), count, count);
        }
    }

    static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url.strip()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
