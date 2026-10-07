package cl.colegiosaas.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Calendario en formato iCalendar (RFC 5545) para agregar al teléfono (NOT-05). Las horas se escriben
 * en UTC (convertidas desde la hora del colegio) y los días completos como fechas: así cualquier
 * aplicación las muestra bien sin necesitar la definición de la zona horaria.
 */
public final class IcsWriter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private IcsWriter() {
    }

    /**
     * @param calendarName nombre que verá la persona en su aplicación ("Colegio San José")
     * @param baseUrl      URL pública del sitio, para el UID y el enlace de cada evento
     */
    public static String write(String calendarName, List<Event> events, ZoneId zone, String baseUrl, Instant now) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Colegio SaaS//Calendario escolar//ES");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:" + escape(calendarName));
        line(ics, "X-WR-TIMEZONE:" + zone.getId());
        String host = baseUrl.replaceFirst("^https?://", "").replaceFirst("[:/].*$", "");
        for (Event event : events) {
            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:event-" + event.getSlug() + "@" + host);
            line(ics, "DTSTAMP:" + UTC.format(event.getUpdatedAt() != null ? event.getUpdatedAt() : now));
            if (event.isAllDay()) {
                line(ics, "DTSTART;VALUE=DATE:" + DATE.format(event.getStartsAt()));
                // En iCalendar el término de un día completo es exclusivo: el día siguiente al último.
                line(ics, "DTEND;VALUE=DATE:" + DATE.format(event.getEndsAt().toLocalDate().plusDays(1)));
            } else {
                line(ics, "DTSTART:" + UTC.format(event.getStartsAt().atZone(zone)));
                line(ics, "DTEND:" + UTC.format(event.getEndsAt().atZone(zone)));
            }
            line(ics, "SUMMARY:" + escape(event.getTitle()));
            if (event.getLocation() != null) {
                line(ics, "LOCATION:" + escape(event.getLocation()));
            }
            if (event.getDescription() != null) {
                line(ics, "DESCRIPTION:" + escape(event.getDescription()));
            }
            line(ics, "URL:" + baseUrl + "/calendario/" + event.getSlug());
            line(ics, "END:VEVENT");
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    /**
     * Una sola cita, para adjuntar al correo de confirmación o descargar desde el enlace de la cita (AGE-04).
     * Trae una alarma un día antes, que el teléfono muestra aunque el correo de recordatorio no llegue.
     *
     * @param uid identificador estable: al reprogramar, la aplicación actualiza la cita en vez de duplicarla
     */
    public static String single(String calendarName, String uid, String summary, String location, String description,
                                LocalDateTime start, LocalDateTime end, ZoneId zone, Instant now) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Colegio SaaS//Agenda//ES");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:" + escape(calendarName));
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:" + uid);
        line(ics, "DTSTAMP:" + UTC.format(now));
        line(ics, "DTSTART:" + UTC.format(start.atZone(zone)));
        line(ics, "DTEND:" + UTC.format(end.atZone(zone)));
        line(ics, "SUMMARY:" + escape(summary));
        if (location != null) {
            line(ics, "LOCATION:" + escape(location));
        }
        if (description != null) {
            line(ics, "DESCRIPTION:" + escape(description));
        }
        line(ics, "BEGIN:VALARM");
        line(ics, "TRIGGER:-P1D");
        line(ics, "ACTION:DISPLAY");
        line(ics, "DESCRIPTION:" + escape(summary));
        line(ics, "END:VALARM");
        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Cada línea termina en CRLF y se pliega a 75 bytes, sin cortar un carácter UTF-8 por la mitad. */
    private static void line(StringBuilder ics, String content) {
        int bytes = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int size = ch.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 75) {
                ics.append(current).append("\r\n ");
                current.setLength(0);
                bytes = 1;
            }
            current.append(ch);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        ics.append(current).append("\r\n");
    }
}
