package cl.colegiosaas.calendar;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cuándo es un evento, en palabras: "12 de octubre, 19:00 h", "13 de julio al 24 de julio".
 * En Thymeleaf: {@code ${@eventDates.when(evento)}}.
 */
@Component("eventDates")
public class EventDates {

    private static final Locale CHILE = Locale.forLanguageTag("es-CL");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d 'de' MMMM", CHILE);
    private static final DateTimeFormatter DAY_WITH_WEEKDAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", CHILE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", CHILE);

    public static String when(Event event) {
        String day = DAY.format(event.getStartsAt());
        boolean severalDays = !event.getStartsAt().toLocalDate().equals(event.getEndsAt().toLocalDate());
        if (severalDays) {
            day = day + " al " + DAY.format(event.getEndsAt());
        }
        if (event.isAllDay()) {
            return day;
        }
        String time = TIME.format(event.getStartsAt()) + " h";
        return severalDays ? day + ", desde las " + time : day + ", " + time;
    }

    /** Para la plantilla (el bean delega en el método estático). */
    public String of(Event event) {
        return when(event);
    }

    /** Horario sin la fecha: "19:00 a 20:30 h" o "Todo el día". */
    public String hours(Event event) {
        if (event.isAllDay()) {
            return "Todo el día";
        }
        return TIME.format(event.getStartsAt()) + " a " + TIME.format(event.getEndsAt()) + " h";
    }

    public String weekday(LocalDate day) {
        return DAY_WITH_WEEKDAY.format(day);
    }
}
