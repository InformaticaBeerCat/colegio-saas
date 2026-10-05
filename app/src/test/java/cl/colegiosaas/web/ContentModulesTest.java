package cl.colegiosaas.web;

import cl.colegiosaas.calendar.CalendarService;
import cl.colegiosaas.calendar.EventDraft;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.calendar.EventRepository;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.info.GeneralInfoService;
import cl.colegiosaas.info.InfoSheetDraft;
import cl.colegiosaas.info.InfoSheetKind;
import cl.colegiosaas.info.WorkshopDraft;
import cl.colegiosaas.news.Announcement;
import cl.colegiosaas.news.AnnouncementRepository;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.StructureService;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comunicados (NOT-03), calendario e .ics (NOT-04, NOT-05), información práctica (PUB-06, 09, 10) y avisos (PUB-12). */
class ContentModulesTest extends WebTestSupport {

    @Autowired
    AnnouncementRepository announcements;

    @Autowired
    CalendarService calendar;

    @Autowired
    EventRepository events;

    @Autowired
    GeneralInfoService info;

    @Autowired
    StructureService structure;

    UserAccount editor;
    int year = Year.now().getValue();

    @BeforeEach
    void setUp() {
        install();
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
        structure.loadChileanLevels();
    }

    // --- Comunicados ---

    @Test
    void announcementsGoToCoursesWithACircularThatOnlyDownloadsWhilePublished() throws Exception {
        Course course = structure.addCourse(level("1° Básico").getId(), "A", year);
        mvc.perform(post("/admin/announcements").with(as(editor)).with(csrf()).param("title", "Paseo de curso"));
        Announcement announcement = announcements.findAll().getFirst();
        String base = "/admin/announcements/" + announcement.getId();

        mvc.perform(post(base).with(as(editor)).with(csrf()).param("title", "Paseo de curso").param("audience", "COURSES"))
                .andExpect(content().string(containsString("Elige al menos un curso")));
        mvc.perform(post(base).with(as(editor)).with(csrf()).param("title", "Paseo de curso")
                .param("body", "<p>Salimos el viernes.</p>").param("audience", "COURSES").param("courseIds", course.getId().toString()));
        mvc.perform(multipart(base + "/attachment").file(new MockMultipartFile("file", "no-es.pdf", "application/pdf", "hola".getBytes()))
                        .with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", "El archivo no es un PDF"));
        mvc.perform(multipart(base + "/attachment").file(new MockMultipartFile("file", "Circular paseo.pdf", "application/pdf", pdf("paseo")))
                .with(as(editor)).with(csrf()));
        mvc.perform(post(base + "/publish").with(as(editor)).with(csrf()));

        MvcResult page = mvc.perform(get("/comunicados"))
                .andExpect(content().string(containsString("Paseo de curso")))
                .andExpect(content().string(containsString("Para: <span>1° Básico A</span>")))
                .andExpect(content().string(containsString("Descargar circular (PDF")))
                .andReturn();
        String link = fileLink(page.getResponse().getContentAsString());
        mvc.perform(get(link))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(pdf("paseo")));

        // Despublicado, la circular deja de descargarse aunque se conozca el enlace.
        mvc.perform(post(base + "/unpublish").with(as(editor)).with(csrf()));
        mvc.perform(get(link)).andExpect(status().isNotFound());
    }

    // --- Calendario ---

    @Test
    void calendarShowsPublishedEventsFilteredAndExportsIcs() throws Exception {
        LocalDate day = LocalDate.now().plusDays(3);
        long meeting = calendar.create(new EventDraft("Reunión de apoderados 1° Básico", EventKind.PARENT_MEETING, false,
                day, LocalTime.of(19, 0), null, LocalTime.of(20, 30), "Sala 12", "Traer libreta",
                Set.of(level("1° Básico").getId()), Set.of())).getId();
        long holiday = calendar.create(new EventDraft("Día del profesor", EventKind.HOLIDAY, true,
                day, null, null, null, null, null, Set.of(), Set.of())).getId();
        long draft = calendar.create(new EventDraft("Evento en borrador", EventKind.OTHER, true,
                day, null, null, null, null, null, Set.of(), Set.of())).getId();
        calendar.publish(meeting);
        calendar.publish(holiday);
        String month = day.toString().substring(0, 7);

        mvc.perform(get("/calendario").param("mes", month))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reunión de apoderados 1° Básico")))
                .andExpect(content().string(containsString("19:00 a 20:30 h")))
                .andExpect(content().string(containsString("Día del profesor")))
                .andExpect(content().string(not(containsString("Evento en borrador"))));
        mvc.perform(get("/calendario").param("mes", month).param("tipo", "holiday"))
                .andExpect(content().string(not(containsString("Reunión de apoderados 1° Básico"))));
        mvc.perform(get("/calendario").param("mes", month).param("nivel", "3-medio"))
                .andExpect(content().string(not(containsString("Reunión de apoderados 1° Básico"))))
                .andExpect(content().string(containsString("Día del profesor")));

        String slug = events.findById(meeting).orElseThrow().getSlug();
        mvc.perform(get("/calendario/" + slug))
                .andExpect(content().string(containsString("Traer libreta")))
                .andExpect(content().string(containsString("/calendario/" + slug + ".ics")));
        mvc.perform(get("/calendario/" + slug + ".ics"))
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(containsString("SUMMARY:Reunión de apoderados 1° Básico")));
        mvc.perform(get("/calendario.ics"))
                .andExpect(content().string(containsString("Día del profesor")))
                .andExpect(content().string(not(containsString("Evento en borrador"))));
        mvc.perform(get("/calendario/" + events.findById(draft).orElseThrow().getSlug())).andExpect(status().isNotFound());
    }

    @Test
    void eventsCannotEndBeforeTheyStart() throws Exception {
        UserAccount admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        mvc.perform(post("/admin/calendar").with(as(admin)).with(csrf())
                        .param("title", "Acto").param("kind", "CEREMONY")
                        .param("startDate", "2026-11-10").param("startTime", "10:00")
                        .param("endDate", "2026-11-09").param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no puede terminar antes de empezar")));
    }

    // --- Información práctica ---

    @Test
    void faqWorkshopsAndSheetsArePublishedOnTheirPages() throws Exception {
        long category = info.addFaqCategory("Uniforme").getId();
        info.saveFaqEntry(null, category, "¿Es obligatorio el uniforme?", "<p>Sí, según el <strong>Reglamento Interno</strong>.</p>", true);
        info.saveFaqEntry(null, category, "Pregunta oculta", "<p>No se ve</p>", false);
        info.saveWorkshop(null, new WorkshopDraft("Robótica", "Construimos robots", "Martes 15:30", "Prof. Soto", 20,
                year, true, Set.of(level("5° Básico").getId())));
        long sheet = info.saveSheet(null, new InfoSheetDraft(InfoSheetKind.SUPPLY_LIST, "Lista de útiles 1° Básico",
                level("1° Básico").getId(), year, null, null, null)).getId();

        mvc.perform(post("/admin/info/sheets/" + sheet + "/publish").with(as(editor)).with(csrf()).param("published", "true"))
                .andExpect(flash().attribute("problem", containsString("antes de publicar")));
        info.saveSheet(sheet, new InfoSheetDraft(InfoSheetKind.SUPPLY_LIST, "Lista de útiles 1° Básico",
                level("1° Básico").getId(), year, "<ul><li>Cuaderno college</li></ul>", null, null));
        info.publishSheet(sheet, true);

        mvc.perform(get("/preguntas-frecuentes"))
                .andExpect(content().string(containsString("¿Es obligatorio el uniforme?")))
                .andExpect(content().string(containsString("<strong>Reglamento Interno</strong>")))
                .andExpect(content().string(not(containsString("Pregunta oculta"))));
        mvc.perform(get("/talleres"))
                .andExpect(content().string(containsString("Robótica")))
                .andExpect(content().string(containsString("5° Básico")));
        mvc.perform(get("/informacion-practica"))
                .andExpect(content().string(containsString("Listas de útiles")))
                .andExpect(content().string(containsString("Cuaderno college")));
    }

    @Test
    void outOfDateMenusAreHidden() throws Exception {
        long menu = info.saveSheet(null, new InfoSheetDraft(InfoSheetKind.MENU, "Minuta de marzo", null, year,
                "<p>Lunes: cazuela</p>", LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1))).getId();
        info.publishSheet(menu, true);

        mvc.perform(get("/informacion-practica")).andExpect(content().string(not(containsString("Minuta de marzo"))));
    }

    // --- Avisos urgentes ---

    @Test
    void urgentAlertsAreTurnedOnAndOffInOneClick() throws Exception {
        mvc.perform(post("/admin/alerts").with(as(editor)).with(csrf())
                .param("message", "Mañana se suspenden las clases").param("severity", "WARNING")
                .param("linkUrl", "/comunicados").param("linkLabel", "Ver comunicado").param("activateNow", "true"));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Mañana se suspenden las clases")))
                .andExpect(content().string(containsString("Ver comunicado")));

        long id = mvc.perform(get("/admin/alerts").with(as(editor))).andReturn().getModelAndView().getModel()
                .get("alerts") instanceof java.util.List<?> list ? ((cl.colegiosaas.site.SiteAlert) list.getFirst()).getId() : 0;
        mvc.perform(post("/admin/alerts/" + id + "/active").with(as(editor)).with(csrf()).param("active", "false"));
        mvc.perform(get("/")).andExpect(content().string(not(containsString("Mañana se suspenden las clases"))));

        mvc.perform(post("/admin/alerts").with(as(editor)).with(csrf())
                        .param("message", "x").param("severity", "INFO").param("linkUrl", "javascript:alert(1)"))
                .andExpect(flash().attribute("problem", containsString("Enlace no permitido")));
    }

    private GradeLevel level(String name) {
        return structure.levels().stream().filter(l -> l.getName().equals(name)).findFirst().orElseThrow();
    }

    private static String fileLink(String html) {
        Matcher matcher = Pattern.compile("href=\"(/archivos/[0-9a-f]{64}/[^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("enlace a /archivos en la página").isTrue();
        return matcher.group(1);
    }
}
