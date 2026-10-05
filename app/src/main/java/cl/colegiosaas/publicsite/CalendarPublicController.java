package cl.colegiosaas.publicsite;

import cl.colegiosaas.calendar.CalendarService;
import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.calendar.IcsWriter;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.text.Slugs;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calendario público (NOT-04) filtrable por tipo y nivel, y exportable a .ics para agregarlo al
 * teléfono (NOT-05). Los filtros van como slugs en la URL.
 */
@Controller
@RequiresFeature(Feature.CALENDAR)
class CalendarPublicController {

    private static final MediaType ICS = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarService calendar;
    private final GradeLevelRepository gradeLevels;
    private final SchoolRepository schools;
    private final SchoolTime time;
    private final AppProperties app;
    private final PublicPages pages;
    private final Clock clock;

    CalendarPublicController(CalendarService calendar, GradeLevelRepository gradeLevels, SchoolRepository schools,
                             SchoolTime time, AppProperties app, PublicPages pages, Clock clock) {
        this.calendar = calendar;
        this.gradeLevels = gradeLevels;
        this.schools = schools;
        this.time = time;
        this.app = app;
        this.pages = pages;
        this.clock = clock;
    }

    @GetMapping("/calendario")
    String month(@RequestParam(name = "mes", required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                 @RequestParam(name = "tipo", required = false) String kindSlug,
                 @RequestParam(name = "nivel", required = false) String levelSlug, Model model) {
        YearMonth shown = month == null ? YearMonth.from(time.today()) : month;
        EventKind kind = kind(kindSlug);
        List<GradeLevel> levels = gradeLevels.findAllByOrderBySortOrderAsc();
        GradeLevel level = level(levels, levelSlug);

        List<Event> events = calendar.published(shown.atDay(1).atStartOfDay(), shown.plusMonths(1).atDay(1).atStartOfDay(), kind, level);
        pages.prepare(model, "Calendario", "Calendario escolar: actos, feriados, vacaciones y reuniones");
        model.addAttribute("month", shown);
        model.addAttribute("days", byDay(events, shown));
        model.addAttribute("kinds", Arrays.stream(EventKind.values()).map(k -> new Option(slug(k), k.name())).toList());
        model.addAttribute("levels", levels.stream().map(l -> new Option(Slugs.slugify(l.getName()), l.getName())).toList());
        model.addAttribute("tipo", kind == null ? null : slug(kind));
        model.addAttribute("nivel", level == null ? null : levelSlug);
        return "public/calendar/month";
    }

    @GetMapping("/calendario/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}")
    String event(@PathVariable String slug, Model model) {
        Event event = calendar.publishedBySlug(slug);
        pages.prepare(model, event.getTitle(), event.getDescription());
        model.addAttribute("event", event);
        return "public/calendar/event";
    }

    /** Suscripción al calendario: el mes pasado y el próximo año, con los mismos filtros de la página. */
    @GetMapping("/calendario.ics")
    ResponseEntity<String> feed(@RequestParam(name = "tipo", required = false) String kindSlug,
                                @RequestParam(name = "nivel", required = false) String levelSlug) {
        LocalDateTime now = time.now();
        List<Event> events = calendar.published(now.minusMonths(1), now.plusYears(1), kind(kindSlug),
                level(gradeLevels.findAllByOrderBySortOrderAsc(), levelSlug));
        return ics(events, "calendario.ics", CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
    }

    @GetMapping("/calendario/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}.ics")
    ResponseEntity<String> single(@PathVariable String slug) {
        Event event = calendar.publishedBySlug(slug);
        return ics(List.of(event), slug + ".ics", CacheControl.noCache());
    }

    private ResponseEntity<String> ics(List<Event> events, String fileName, CacheControl cache) {
        String name = schools.findSingleton().map(School::getName).orElse("Calendario escolar");
        String body = IcsWriter.write(name, events, time.zone(), app.baseUrl(), clock.instant());
        return ResponseEntity.ok()
                .contentType(ICS)
                .cacheControl(cache)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
                .body(body);
    }

    /** Agrupa por día; un evento de varios días aparece en el primer día que cae dentro del mes. */
    private static List<DayGroup> byDay(List<Event> events, YearMonth month) {
        Map<LocalDate, List<Event>> days = new TreeMap<>();
        for (Event event : events) {
            LocalDate day = event.getStartsAt().toLocalDate();
            if (day.isBefore(month.atDay(1))) {
                day = month.atDay(1);
            }
            days.computeIfAbsent(day, d -> new ArrayList<>()).add(event);
        }
        return days.entrySet().stream().map(e -> new DayGroup(e.getKey(), e.getValue())).toList();
    }

    static String slug(EventKind kind) {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static EventKind kind(String slug) {
        return slug == null ? null : Arrays.stream(EventKind.values()).filter(k -> slug(k).equals(slug)).findFirst().orElse(null);
    }

    private static GradeLevel level(List<GradeLevel> levels, String slug) {
        return slug == null ? null : levels.stream().filter(l -> Slugs.slugify(l.getName()).equals(slug)).findFirst().orElse(null);
    }

    record DayGroup(LocalDate day, List<Event> events) {
    }

    /** Opción de filtro: slug para la URL y valor para mostrar (nombre o clave de mensaje). */
    record Option(String slug, String label) {
    }
}
