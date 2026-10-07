package cl.colegiosaas.scheduling;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Trae los feriados de Chile desde una fuente oficial (por defecto la API de feriados de gob.cl): cambian por
 * ley de un año a otro y no se escriben a mano. Si la fuente no responde, el panel permite agregarlos uno a uno.
 */
@Component
public class HolidayImporter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AgendaProperties properties;
    private final RestClient http;

    HolidayImporter(AgendaProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public static class ImportFailed extends RuntimeException {
        ImportFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public List<Holiday> fetch(int year) {
        String url = properties.holidaysUrl().replace("{year}", String.valueOf(year));
        try {
            String body = http.get().uri(url).header("Accept", "application/json").retrieve().body(String.class);
            return parse(body, year);
        } catch (RestClientException e) {
            throw new ImportFailed("No se pudo consultar la fuente oficial de feriados. Agrégalos a mano o inténtalo más tarde.", e);
        }
    }

    /**
     * Formato de la API de gob.cl: {@code [{"nombre": "Año Nuevo", "fecha": "2026-01-01", "irrenunciable": "1"}, …]}.
     * Algunas versiones envuelven la lista en {@code {"data": […]}}. Solo se aceptan fechas del año pedido.
     */
    static List<Holiday> parse(String body, int year) {
        try {
            JsonNode root = JSON.readTree(body == null ? "" : body);
            JsonNode list = root.isArray() ? root : root.path("data");
            if (!list.isArray()) {
                throw new ImportFailed("La fuente de feriados respondió en un formato desconocido.", null);
            }
            List<Holiday> holidays = new ArrayList<>();
            for (JsonNode item : list) {
                LocalDate date = LocalDate.parse(item.path("fecha").asString(""));
                String name = item.path("nombre").asString("").strip();
                if (date.getYear() != year || name.isEmpty()) {
                    continue;
                }
                String mandatory = item.path("irrenunciable").asString("0");
                holidays.add(new Holiday(date, name.length() > 120 ? name.substring(0, 120) : name,
                        "1".equals(mandatory) || "true".equalsIgnoreCase(mandatory)));
            }
            return holidays;
        } catch (JacksonException | DateTimeParseException e) {
            throw new ImportFailed("La fuente de feriados respondió en un formato desconocido.", e);
        }
    }
}
