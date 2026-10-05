package cl.colegiosaas.calendar;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.text.Slugs;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.CourseRepository;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;

/** Calendario escolar (NOT-04): actos, feriados, vacaciones, reuniones por curso. */
@Service
public class CalendarService {

    private final EventRepository events;
    private final GradeLevelRepository gradeLevels;
    private final CourseRepository courses;
    private final AuditTrail audit;

    CalendarService(EventRepository events, GradeLevelRepository gradeLevels, CourseRepository courses, AuditTrail audit) {
        this.events = events;
        this.gradeLevels = gradeLevels;
        this.courses = courses;
        this.audit = audit;
    }

    /** Eventos de un mes para el panel, publicados o no. */
    @Transactional(readOnly = true)
    public List<Event> month(YearMonth month) {
        return events.findBetween(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public Event get(long id) {
        return events.findWithTargetsById(id).orElseThrow(() -> new NotFound("El evento no existe"));
    }

    @Transactional
    public Event create(EventDraft draft) {
        checkDraft(draft);
        String slug = Slugs.unique(draft.title() + " " + draft.startDate(), "evento", events::existsBySlug);
        Event event = new Event(slug, draft.title().strip(), draft.kind(), start(draft), end(draft));
        apply(event, draft);
        events.save(event);
        audit.record(AuditAction.CREATE, "Event", event.getId(), event.getTitle());
        return event;
    }

    @Transactional
    public void update(long id, EventDraft draft) {
        checkDraft(draft);
        Event event = get(id);
        event.setTitle(draft.title().strip());
        event.setKind(draft.kind());
        apply(event, draft);
        audit.record(AuditAction.UPDATE, "Event", id, event.getTitle());
    }

    @Transactional
    public void publish(long id) {
        Event event = get(id);
        event.publish();
        audit.record(AuditAction.PUBLISH, "Event", id, event.getTitle());
    }

    @Transactional
    public void unpublish(long id) {
        Event event = get(id);
        event.unpublish();
        audit.record(AuditAction.UNPUBLISH, "Event", id, event.getTitle());
    }

    /** Un evento con inscripciones no se borra (son datos de personas): se despublica. */
    @Transactional
    public void delete(long id) {
        Event event = get(id);
        try {
            events.delete(event);
            events.flush();
        } catch (DataIntegrityViolationException e) {
            throw new RuleViolation("El evento tiene inscripciones; despublícalo en vez de eliminarlo");
        }
        audit.record(AuditAction.DELETE, "Event", id, event.getTitle());
    }

    // --- Sitio público ---

    /**
     * Eventos publicados que se cruzan con el rango, filtrados por tipo y nivel. Un evento sin niveles
     * es de todo el colegio: aparece con cualquier filtro de nivel.
     */
    @Transactional(readOnly = true)
    public List<Event> published(LocalDateTime from, LocalDateTime to, EventKind kind, GradeLevel level) {
        return events.findPublishedBetween(from, to).stream()
                .filter(e -> kind == null || e.getKind() == kind)
                .filter(e -> level == null || e.getGradeLevels().isEmpty() || e.getGradeLevels().contains(level))
                .toList();
    }

    @Transactional(readOnly = true)
    public Event publishedBySlug(String slug) {
        return events.findBySlug(slug).filter(Event::isPublished).orElseThrow(() -> new NotFound("El evento no existe"));
    }

    private void apply(Event event, EventDraft draft) {
        if (draft.allDay()) {
            event.rescheduleAllDay(start(draft), end(draft));
        } else {
            event.reschedule(start(draft), end(draft));
        }
        event.setLocation(blankToNull(draft.location()));
        event.setDescription(blankToNull(draft.description()));
        event.targetGradeLevels(new HashSet<>(gradeLevels.findAllById(draft.gradeLevelIds())));
        event.targetCourses(new HashSet<>(courses.findAllById(draft.courseIds())));
    }

    private static void checkDraft(EventDraft draft) {
        if (draft.title() == null || draft.title().isBlank()) {
            throw new RuleViolation("El evento necesita un título");
        }
        if (draft.kind() == null) {
            throw new RuleViolation("Elige el tipo de evento");
        }
        if (draft.startDate() == null) {
            throw new RuleViolation("Indica la fecha del evento");
        }
        if (!draft.allDay() && draft.startTime() == null) {
            throw new RuleViolation("Indica la hora de inicio o marca \"todo el día\"");
        }
        if (draft.description() != null && draft.description().length() > 2000) {
            throw new RuleViolation("La descripción admite hasta 2000 caracteres");
        }
        if (end(draft).isBefore(start(draft))) {
            throw new RuleViolation("El evento no puede terminar antes de empezar");
        }
    }

    private static LocalDateTime start(EventDraft draft) {
        return draft.allDay() ? draft.startDate().atStartOfDay() : draft.startDate().atTime(draft.startTime());
    }

    /** Sin término: un día completo dura ese día; un evento con hora, una hora. */
    private static LocalDateTime end(EventDraft draft) {
        if (draft.allDay()) {
            return endDate(draft).atTime(LocalTime.of(23, 59));
        }
        if (draft.endTime() == null) {
            return start(draft).plusHours(1);
        }
        return endDate(draft).atTime(draft.endTime());
    }

    private static LocalDate endDate(EventDraft draft) {
        return draft.endDate() == null ? draft.startDate() : draft.endDate();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
