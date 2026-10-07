package cl.colegiosaas.platform;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos de fecha para las vistas, en la zona horaria del colegio. En Thymeleaf se usa como
 * {@code ${@formats.dateTime(algo)}}.
 */
@Component("formats")
public class Formats {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final Locale CHILE = Locale.forLanguageTag("es-CL");
    /** "12 de octubre de 2026": fechas en el sitio público. */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", CHILE);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", CHILE);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", CHILE);

    private final SchoolRepository schools;

    Formats(SchoolRepository schools) {
        this.schools = schools;
    }

    public String date(Instant instant) {
        if (instant == null) {
            return "";
        }
        return LONG_DATE.format(instant.atZone(zone()));
    }

    /** "octubre de 2026". */
    public String month(YearMonth month) {
        return MONTH.format(month);
    }

    /** "1,2 MB", "340 KB": tamaño de descargas, para que nadie baje 20 MB sin saberlo. */
    public String size(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return String.format(CHILE, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public String date(LocalDate day) {
        return day == null ? "" : LONG_DATE.format(day);
    }

    /** "Martes 13 de octubre": títulos de días en la agenda, donde el día de la semana importa más que el año. */
    public String weekday(LocalDate day) {
        if (day == null) {
            return "";
        }
        String text = WEEKDAY.format(day);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public String dateTime(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return DATE_TIME.format(instant.atZone(zone()));
    }

    private ZoneId zone() {
        return ZoneId.of(schools.findSingleton().map(School::getTimeZone).orElse("America/Santiago"));
    }
}
