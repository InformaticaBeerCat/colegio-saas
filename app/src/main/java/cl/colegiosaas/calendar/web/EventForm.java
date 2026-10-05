package cl.colegiosaas.calendar.web;

import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventDraft;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.shared.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class EventForm {

    private String title;
    private EventKind kind = EventKind.OTHER;
    private boolean allDay;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;
    private String location;
    private String description;
    private Set<Long> gradeLevelIds = new HashSet<>();
    private Set<Long> courseIds = new HashSet<>();

    static EventForm of(Event e) {
        EventForm form = new EventForm();
        form.title = e.getTitle();
        form.kind = e.getKind();
        form.allDay = e.isAllDay();
        form.startDate = e.getStartsAt().toLocalDate();
        form.endDate = e.getEndsAt().toLocalDate();
        if (!e.isAllDay()) {
            form.startTime = e.getStartsAt().toLocalTime();
            form.endTime = e.getEndsAt().toLocalTime();
        }
        form.location = e.getLocation();
        form.description = e.getDescription();
        form.gradeLevelIds = e.getGradeLevels().stream().map(BaseEntity::getId).collect(Collectors.toSet());
        form.courseIds = e.getCourses().stream().map(BaseEntity::getId).collect(Collectors.toSet());
        return form;
    }

    EventDraft toDraft() {
        return new EventDraft(title, kind, allDay, startDate, startTime, endDate, endTime, location, description,
                gradeLevelIds, courseIds);
    }
}
