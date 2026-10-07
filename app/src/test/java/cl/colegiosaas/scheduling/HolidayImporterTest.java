package cl.colegiosaas.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayImporterTest {

    @Test
    void readsTheOfficialFormatAndKeepsOnlyTheRequestedYear() {
        String body = """
                [
                  {"nombre": "Año Nuevo", "comentarios": null, "fecha": "2026-01-01", "irrenunciable": "1", "tipo": "Civil"},
                  {"nombre": "Viernes Santo", "fecha": "2026-04-03", "irrenunciable": "0", "tipo": "Religioso"},
                  {"nombre": "De otro año", "fecha": "2027-01-01", "irrenunciable": "1"}
                ]
                """;
        List<Holiday> holidays = HolidayImporter.parse(body, 2026);
        assertThat(holidays).extracting(Holiday::getHolidayDate).containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 3));
        assertThat(holidays.get(0).isMandatory()).isTrue();
        assertThat(holidays.get(1).isMandatory()).isFalse();
        assertThat(holidays.get(1).getName()).isEqualTo("Viernes Santo");
    }

    @Test
    void acceptsTheWrappedFormatAndRejectsAnythingElse() {
        assertThat(HolidayImporter.parse("{\"data\": [{\"nombre\": \"Año Nuevo\", \"fecha\": \"2026-01-01\"}]}", 2026)).hasSize(1);
        assertThatThrownBy(() -> HolidayImporter.parse("<html>error</html>", 2026)).isInstanceOf(HolidayImporter.ImportFailed.class);
        assertThatThrownBy(() -> HolidayImporter.parse("{\"error\": \"x\"}", 2026)).isInstanceOf(HolidayImporter.ImportFailed.class);
    }
}
