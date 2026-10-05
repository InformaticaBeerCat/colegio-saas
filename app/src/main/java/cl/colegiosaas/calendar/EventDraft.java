package cl.colegiosaas.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Datos de un evento tal como se escriben: fechas y horas en hora del colegio.
 * En un evento de día completo las horas se ignoran.
 */
public record EventDraft(
        String title,
        EventKind kind,
        boolean allDay,
        LocalDate startDate,
        LocalTime startTime,
        LocalDate endDate,
        LocalTime endTime,
        String location,
        String description,
        Set<Long> gradeLevelIds,
        Set<Long> courseIds) {

    public EventDraft {
        gradeLevelIds = gradeLevelIds == null ? Set.of() : Set.copyOf(gradeLevelIds);
        courseIds = courseIds == null ? Set.of() : Set.copyOf(courseIds);
    }
}
