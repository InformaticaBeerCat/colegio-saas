package cl.colegiosaas.platform;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Hora del colegio. Las fechas de calendario se guardan en hora local del colegio y las marcas técnicas
 * en UTC; aquí se convierte entre ambas con la zona del perfil (continental, Magallanes o Isla de Pascua).
 */
@Component
public class SchoolTime {

    private static final String DEFAULT_ZONE = "America/Santiago";

    private final SchoolRepository schools;
    private final Clock clock;

    SchoolTime(SchoolRepository schools, Clock clock) {
        this.schools = schools;
        this.clock = clock;
    }

    public ZoneId zone() {
        return ZoneId.of(schools.findSingleton().map(School::getTimeZone).orElse(DEFAULT_ZONE));
    }

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), zone());
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    /** "Publicar el lunes a las 8:00" se escribe en hora del colegio y se guarda como instante. */
    public Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(zone()).toInstant();
    }

    public LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone());
    }
}
