package cl.colegiosaas.calendar.web;

import cl.colegiosaas.calendar.CalendarService;
import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.CourseRepository;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;

/** Calendario escolar en el panel (NOT-04). */
@Controller
@RequestMapping("/admin/calendar")
@RequiresFeature(Feature.CALENDAR)
@PreAuthorize("hasAuthority('CALENDAR')")
class CalendarAdminController {

    private final CalendarService calendar;
    private final GradeLevelRepository gradeLevels;
    private final CourseRepository courses;
    private final SchoolTime time;

    CalendarAdminController(CalendarService calendar, GradeLevelRepository gradeLevels, CourseRepository courses, SchoolTime time) {
        this.calendar = calendar;
        this.gradeLevels = gradeLevels;
        this.courses = courses;
        this.time = time;
    }

    /** {@code mes} en formato 2026-10; por defecto el mes actual. */
    @GetMapping
    String list(@RequestParam(name = "mes", required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
        YearMonth shown = month == null ? YearMonth.from(time.today()) : month;
        model.addAttribute("month", shown);
        model.addAttribute("events", calendar.month(shown));
        return "admin/calendar/list";
    }

    @GetMapping("/new")
    String newEvent(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        EventForm form = new EventForm();
        form.setStartDate(date == null ? time.today() : date);
        return show(null, form, model);
    }

    @PostMapping
    String create(@ModelAttribute("form") EventForm form, Model model, RedirectAttributes redirect) {
        try {
            Event event = calendar.create(form.toDraft());
            redirect.addFlashAttribute("notice", "Evento creado. Publícalo para que aparezca en el calendario del sitio.");
            return "redirect:/admin/calendar/" + event.getId();
        } catch (RuleViolation e) {
            model.addAttribute("problem", e.getMessage());
            return show(null, form, model);
        }
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        Event event = calendar.get(id);
        return show(event, EventForm.of(event), model);
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @ModelAttribute("form") EventForm form, Model model, RedirectAttributes redirect) {
        try {
            calendar.update(id, form.toDraft());
            redirect.addFlashAttribute("notice", "Evento guardado");
            return "redirect:/admin/calendar/" + id;
        } catch (RuleViolation e) {
            model.addAttribute("problem", e.getMessage());
            return show(calendar.get(id), form, model);
        }
    }

    @PostMapping("/{id}/publish")
    String publish(@PathVariable long id, RedirectAttributes redirect) {
        calendar.publish(id);
        redirect.addFlashAttribute("notice", "Evento publicado");
        return "redirect:/admin/calendar/" + id;
    }

    @PostMapping("/{id}/unpublish")
    String unpublish(@PathVariable long id, RedirectAttributes redirect) {
        calendar.unpublish(id);
        redirect.addFlashAttribute("notice", "Evento retirado del calendario público");
        return "redirect:/admin/calendar/" + id;
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        Event event = calendar.get(id);
        try {
            calendar.delete(id);
            redirect.addFlashAttribute("notice", "Evento eliminado");
            return "redirect:/admin/calendar?mes=" + YearMonth.from(event.getStartsAt());
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/admin/calendar/" + id;
        }
    }

    private String show(Event event, EventForm form, Model model) {
        model.addAttribute("event", event);
        model.addAttribute("form", form);
        model.addAttribute("kinds", EventKind.values());
        model.addAttribute("gradeLevels", gradeLevels.findAllByOrderBySortOrderAsc());
        int year = form.getStartDate() == null ? time.today().getYear() : form.getStartDate().getYear();
        model.addAttribute("courses", courses.findByAcademicYearOrderByGradeLevel_SortOrderAscSectionAsc(year));
        return "admin/calendar/edit";
    }
}
