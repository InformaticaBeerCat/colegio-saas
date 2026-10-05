package cl.colegiosaas.calendar;

import cl.colegiosaas.media.Album;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Entrada del calendario escolar (NOT-04). Un solo modelo para feriados, vacaciones, actos,
 * reuniones de apoderados por curso (AGE-08) y eventos con inscripción (EVE-01, EVE-02).
 *
 * Las fechas son hora local del colegio ({@code School.timeZone}): "reunión a las 19:00"
 * se guarda tal cual. Las marcas técnicas (creado, publicado) van en UTC.
 */
@Entity
@Table(name = "calendar_event")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseEntity {

    @NotBlank
    @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
    private String slug;

    @NotBlank
    private String title;

    @Size(max = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private EventKind kind;

    @Setter(AccessLevel.NONE)
    private LocalDateTime startsAt;

    @Setter(AccessLevel.NONE)
    private LocalDateTime endsAt;

    @Setter(AccessLevel.NONE)
    private boolean allDay;

    private String location;

    /** Público por nivel para filtrar el calendario; vacío = todo el colegio. */
    @ManyToMany
    @JoinTable(name = "calendar_event_grade_level",
            joinColumns = @JoinColumn(name = "calendar_event_id"),
            inverseJoinColumns = @JoinColumn(name = "grade_level_id"))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<GradeLevel> gradeLevels = new HashSet<>();

    /** Cursos convocados (reuniones de apoderados). */
    @ManyToMany
    @JoinTable(name = "calendar_event_course",
            joinColumns = @JoinColumn(name = "calendar_event_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<Course> courses = new HashSet<>();

    /** Fotos del evento, cuando las haya. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Album album;

    @Setter(AccessLevel.NONE)
    private Instant publishedAt;

    // --- Inscripción (EVE-02) ---

    @Setter(AccessLevel.NONE)
    private boolean registrationEnabled;

    /** Personas que caben; nulo = sin límite. */
    @Setter(AccessLevel.NONE)
    private Integer capacity;

    @Setter(AccessLevel.NONE)
    private boolean waitlistEnabled;

    /** Cierre automático; nulo = cierra cuando empieza el evento. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime registrationClosesAt;

    public Event(String slug, String title, EventKind kind, LocalDateTime startsAt, LocalDateTime endsAt) {
        this.slug = slug;
        this.title = title;
        this.kind = kind;
        reschedule(startsAt, endsAt);
    }

    /** Evento de día completo, p. ej. vacaciones de invierno del 13 al 24 de julio. */
    public static Event allDay(String slug, String title, EventKind kind, LocalDate from, LocalDate to) {
        Event event = new Event(slug, title, kind, from.atStartOfDay(), to.atTime(LocalTime.of(23, 59)));
        event.allDay = true;
        return event;
    }

    public void reschedule(LocalDateTime newStart, LocalDateTime newEnd) {
        Objects.requireNonNull(newStart, "startsAt");
        Objects.requireNonNull(newEnd, "endsAt");
        if (newEnd.isBefore(newStart)) {
            throw new IllegalArgumentException("El evento no puede terminar antes de empezar");
        }
        startsAt = newStart;
        endsAt = newEnd;
        allDay = false;
    }

    public void publish() {
        publishedAt = Instant.now();
    }

    public void unpublish() {
        publishedAt = null;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    // --- Inscripción ---

    public void openRegistration(Integer capacity, boolean waitlistEnabled, LocalDateTime closesAt) {
        if (capacity != null && capacity < 1) {
            throw new IllegalArgumentException("El aforo debe ser al menos 1");
        }
        this.registrationEnabled = true;
        this.capacity = capacity;
        this.waitlistEnabled = waitlistEnabled && capacity != null;
        this.registrationClosesAt = closesAt;
    }

    public void closeRegistration() {
        registrationEnabled = false;
    }

    public boolean acceptsRegistrationsAt(LocalDateTime now) {
        LocalDateTime closesAt = registrationClosesAt != null ? registrationClosesAt : startsAt;
        return registrationEnabled && isPublished() && now.isBefore(closesAt);
    }

    /** Cupos que quedan dadas las personas ya confirmadas; vacío si no hay límite. */
    public OptionalInt seatsLeft(int confirmedAttendees) {
        return capacity == null ? OptionalInt.empty() : OptionalInt.of(Math.max(0, capacity - confirmedAttendees));
    }

    // --- Público ---

    public void targetGradeLevels(Set<GradeLevel> levels) {
        gradeLevels.clear();
        gradeLevels.addAll(levels);
    }

    public void targetCourses(Set<Course> targetCourses) {
        courses.clear();
        courses.addAll(targetCourses);
    }

    public Set<GradeLevel> getGradeLevels() {
        return Collections.unmodifiableSet(gradeLevels);
    }

    public Set<Course> getCourses() {
        return Collections.unmodifiableSet(courses);
    }
}
