package cl.colegiosaas.platform.ops;

import cl.colegiosaas.platform.PlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Canal de actualización (OPS-03): una vez al día consulta si hay una versión nueva y la muestra en el panel del
 * proveedor. Instalarla es reemplazar la imagen; las migraciones de la base corren solas al arrancar (OPS-04).
 * Acepta el formato de las versiones de GitHub ({@code tag_name}, {@code html_url}, {@code body}) o uno propio
 * ({@code version}, {@code url}, {@code notes}).
 */
@Component
public class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Una versión publicada. */
    public record Release(String version, String url, String notes, Instant checkedAt) {
    }

    private final PlatformProperties properties;
    private final String currentVersion;
    private final RestClient http;
    private final AtomicReference<Release> latest = new AtomicReference<>();

    UpdateChecker(PlatformProperties properties, ObjectProvider<BuildProperties> build) {
        this.properties = properties;
        BuildProperties info = build.getIfAvailable();
        this.currentVersion = info == null ? "desarrollo" : info.getVersion();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public String currentVersion() {
        return currentVersion;
    }

    /** La versión nueva disponible, si la última consulta encontró una mayor que la instalada. */
    public Optional<Release> available() {
        Release release = latest.get();
        return release != null && isNewer(release.version(), currentVersion) ? Optional.of(release) : Optional.empty();
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "P1D")
    public void check() {
        if (properties.updatesFeedUrl().isBlank()) {
            return;
        }
        try {
            String body = http.get().uri(properties.updatesFeedUrl()).header("Accept", "application/json").retrieve().body(String.class);
            latest.set(parse(body));
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("No se pudo consultar el canal de actualizaciones: {}", e.getMessage());
        }
    }

    static Release parse(String body) {
        try {
            JsonNode data = JSON.readTree(body == null ? "" : body);
            String version = text(data, "version", "tag_name");
            if (version == null) {
                throw new IllegalArgumentException("El canal de actualizaciones no informa la versión");
            }
            return new Release(version.replaceFirst("^v", ""), text(data, "url", "html_url"), text(data, "notes", "body"), Instant.now());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("El canal de actualizaciones respondió en un formato desconocido", e);
        }
    }

    /** Compara versiones "1.10.2" por número; una versión de desarrollo nunca se considera al día. */
    static boolean isNewer(String candidate, String current) {
        if (current == null || !current.matches("\\d+(\\.\\d+)*.*")) {
            return true;
        }
        String[] a = candidate.replaceFirst("-.*$", "").split("\\.");
        String[] b = current.replaceFirst("-.*$", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? number(a[i]) : 0;
            int y = i < b.length ? number(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        // Misma versión numérica: la publicada gana a una instantánea (1.2.0 > 1.2.0-SNAPSHOT).
        return current.contains("-") && !candidate.contains("-");
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String text(JsonNode data, String... names) {
        for (String name : names) {
            JsonNode node = data.path(name);
            if (node.isString() && !node.asString().isBlank()) {
                return node.asString();
            }
        }
        return null;
    }
}
