package cl.colegiosaas.calendar;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcsWriterTest {

    static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

    @Test
    void timedEventsAreWrittenInUtcAndAllDayEventsWithAnExclusiveEnd() {
        Event meeting = new Event("reunion-1-a", "Reunión de apoderados 1° A", EventKind.PARENT_MEETING,
                LocalDateTime.of(2026, 10, 12, 19, 0), LocalDateTime.of(2026, 10, 12, 20, 30));
        meeting.setLocation("Sala 12, segundo piso");
        Event vacation = Event.allDay("vacaciones", "Vacaciones de fiestas patrias", EventKind.VACATION,
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 19));

        String ics = IcsWriter.write("Colegio San José", List.of(meeting, vacation), SANTIAGO, "https://colegio.cl", Instant.EPOCH);

        // Octubre en Santiago es horario de verano (UTC-3): 19:00 local = 22:00 UTC.
        assertThat(ics).contains("DTSTART:20261012T220000Z", "DTEND:20261012T233000Z")
                .contains("DTSTART;VALUE=DATE:20260917", "DTEND;VALUE=DATE:20260920")
                .contains("UID:event-reunion-1-a@colegio.cl")
                .contains("LOCATION:Sala 12\\, segundo piso")
                .contains("URL:https://colegio.cl/calendario/vacaciones")
                .startsWith("BEGIN:VCALENDAR\r\n")
                .endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void longLinesAreFoldedWithoutBreakingCharacters() {
        Event event = new Event("largo", "Ceremonia de licenciatura de cuarto año medio con entrega de premios y distinciones",
                EventKind.CEREMONY, LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 1, 12, 0));
        event.setDescription("Ñandú ".repeat(30));

        String ics = IcsWriter.write("Colegio", List.of(event), SANTIAGO, "https://colegio.cl", Instant.EPOCH);

        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("SUMMARY:Ceremonia de licenciatura de cuarto año medio con entrega de premios y distinciones");
        assertThat(unfolded).contains("Ñandú Ñandú");
    }

    @Test
    void specialCharactersAreEscaped() {
        assertThat(IcsWriter.escape("Lunes; martes, miércoles\nfin\\")).isEqualTo("Lunes\\; martes\\, miércoles\\nfin\\\\");
    }
}
