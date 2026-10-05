package cl.colegiosaas.platform;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formatos de fecha para las vistas, en la zona horaria del colegio. En Thymeleaf se usa como
 * {@code ${@formats.dateTime(algo)}}.
 */
@Component("formats")
public class Formats {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final SchoolRepository schools;

    Formats(SchoolRepository schools) {
        this.schools = schools;
    }

    public String dateTime(Instant instant) {
        if (instant == null) {
            return "—";
        }
        ZoneId zone = ZoneId.of(schools.findSingleton().map(School::getTimeZone).orElse("America/Santiago"));
        return DATE_TIME.format(instant.atZone(zone));
    }
}
