package cl.colegiosaas.web;

import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.calendar.EventRegistration;
import cl.colegiosaas.calendar.EventRegistrationRepository;
import cl.colegiosaas.calendar.EventRepository;
import cl.colegiosaas.calendar.RegistrationStatus;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.CourseRepository;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import cl.colegiosaas.support.WebTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inscripción a eventos con cupos, lista de espera y cierre (EVE-01, EVE-02) y reuniones por curso (AGE-08). */
class EventRegistrationTest extends WebTestSupport {

    @Autowired LegalTextService legalTexts;
    @Autowired EventRepository events;
    @Autowired EventRegistrationRepository registrations;
    @Autowired GradeLevelRepository levels;
    @Autowired CourseRepository courses;
    @Autowired SchoolRepository schools;
    @Autowired SchoolTime time;
    @Autowired EntityManager em;

    UserAccount admin;
    UserAccount editor;
    Event openHouse;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_EVENTS, admin.getId());
        LocalDateTime start = time.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        openHouse = new Event("puertas-abiertas", "Puertas abiertas 2027", EventKind.OPEN_HOUSE, start, start.plusHours(2));
        openHouse.setLocation("Gimnasio");
        openHouse.publish();
        events.save(openHouse);
    }

    @Test
    void theEditorOpensRegistrationWithCapacityAndWaitlist() throws Exception {
        mvc.perform(get("/calendario/puertas-abiertas")).andExpect(content().string(not(containsString("id=\"inscripcion\""))));
        mvc.perform(get("/admin/calendar/" + openHouse.getId()).with(as(editor)))
                .andExpect(content().string(containsString("Inscripción e inscritos")));

        mvc.perform(post("/admin/events/" + openHouse.getId() + "/registrations/settings").param("enabled", "true")
                        .param("capacity", "0").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("al menos 1")));
        mvc.perform(post("/admin/events/" + openHouse.getId() + "/registrations/settings").param("enabled", "true")
                        .param("capacity", "3").param("waitlist", "true")
                        .param("closesAt", openHouse.getStartsAt().plusHours(1).toString()).with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("antes de que empiece")));
        mvc.perform(post("/admin/events/" + openHouse.getId() + "/registrations/settings").param("enabled", "true")
                        .param("capacity", "3").param("waitlist", "true").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("notice", "Inscripción abierta"));

        mvc.perform(get("/calendario/puertas-abiertas"))
                .andExpect(content().string(containsString("Quedan 3 cupo(s).")))
                .andExpect(content().string(containsString("Aviso de privacidad: inscripción a eventos")))
                .andExpect(content().string(containsString("name=\"sello\"")));
    }

    @Test
    void seatsRunOutTheWaitlistFillsAndACancellationPromotesTheNextFamily() throws Exception {
        openHouse.openRegistration(3, true, null);

        mvc.perform(register("rosa@correo.cl", 2).param("consent", "false"))
                .andExpect(content().string(containsString("marca la casilla")));
        mvc.perform(register("rosa@correo.cl", 2).param("consent", "true"))
                .andExpect(redirectedUrl("/calendario/puertas-abiertas"))
                .andExpect(flash().attribute("notice", containsString("confirmada")));
        String rosaLink = manageLinkFor("rosa@correo.cl");

        mvc.perform(register("rosa@correo.cl", 1).param("consent", "true"))
                .andExpect(content().string(containsString("Ya hay una inscripción con ese correo")));

        // Quedan 1 cupo: una familia de 2 va a la lista de espera completa, no se separa.
        mvc.perform(register("luis@correo.cl", 2).param("consent", "true"))
                .andExpect(flash().attribute("notice", containsString("lugar 1 de la lista de espera")));
        mvc.perform(register("ana@correo.cl", 1).param("consent", "true"))
                .andExpect(flash().attribute("notice", containsString("confirmada")));
        mvc.perform(get("/calendario/puertas-abiertas"))
                .andExpect(content().string(containsString("Inscribirme en la lista de espera")));
        mvc.perform(register("pia@correo.cl", 1).param("consent", "true"))
                .andExpect(flash().attribute("notice", containsString("lugar 2")));

        EventRegistration luis = byEmail("luis@correo.cl");
        assertThat(luis.getStatus()).isEqualTo(RegistrationStatus.WAITLISTED);
        assertThat(luis.getConsent().getLegalText().getKind()).isEqualTo(LegalTextKind.NOTICE_EVENTS);

        // Rosa cancela con su enlace: se liberan 2 y sube Luis (2 personas); Pía sigue esperando.
        mvc.perform(get(rosaLink))
                .andExpect(content().string(containsString("Confirmada")))
                .andExpect(content().string(containsString("noindex")));
        mvc.perform(post(rosaLink + "/cancelar").with(csrf())).andExpect(redirectedUrl(rosaLink));
        em.flush();
        em.clear();
        assertThat(byEmail("rosa@correo.cl").getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(byEmail("luis@correo.cl").getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(byEmail("luis@correo.cl").getWaitlistPosition()).isNull();
        assertThat(byEmail("pia@correo.cl").getStatus()).isEqualTo(RegistrationStatus.WAITLISTED);
        assertThat(mails()).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("luis@correo.cl");
            assertThat(m.subject()).startsWith("Se liberó un cupo");
        });

        // Ampliar el aforo sube al resto de la lista.
        mvc.perform(post("/admin/events/" + openHouse.getId() + "/registrations/settings").param("enabled", "true")
                .param("capacity", "4").param("waitlist", "true").with(as(editor)).with(csrf()));
        em.flush();
        em.clear();
        assertThat(byEmail("pia@correo.cl").getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);

        mvc.perform(get("/admin/events/" + openHouse.getId() + "/registrations").with(as(editor)))
                .andExpect(content().string(containsString("4 persona(s) confirmada(s)")))
                .andExpect(content().string(containsString("luis@correo.cl")));
        String csv = mvc.perform(get("/admin/events/" + openHouse.getId() + "/registrations/export").with(as(editor)))
                .andExpect(header().string("Content-Disposition", containsString("inscritos-puertas-abiertas.csv")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Estado;Lugar en espera;Nombre", "luis@correo.cl", "CANCELLED");
    }

    @Test
    void withoutWaitlistAFullEventRejectsAndRegistrationClosesAutomatically() throws Exception {
        openHouse.openRegistration(1, false, null);
        mvc.perform(register("rosa@correo.cl", 2).param("consent", "true"))
                .andExpect(content().string(containsString("Quedan 1 cupo(s): inscribe a menos personas")));
        mvc.perform(register("rosa@correo.cl", 1).param("consent", "true"))
                .andExpect(flash().attribute("notice", containsString("confirmada")));
        mvc.perform(get("/calendario/puertas-abiertas"))
                .andExpect(content().string(containsString("Se agotaron los cupos.")))
                .andExpect(content().string(not(containsString("action=\"/calendario/puertas-abiertas/inscripcion\""))));

        openHouse.openRegistration(10, false, time.now().minusMinutes(1));
        mvc.perform(get("/calendario/puertas-abiertas")).andExpect(content().string(containsString("La inscripción está cerrada")));
        mvc.perform(register("luis@correo.cl", 1).param("consent", "true"))
                .andExpect(content().string(containsString("La inscripción para este evento está cerrada")));
    }

    @Test
    void parentMeetingsAskForTheCourseAndAreListedByCourse() throws Exception {
        GradeLevel first = levels.save(new GradeLevel("1° Básico", EducationStage.PRIMARY, null, 1));
        Course a = courses.save(new Course(first, "A", time.today().getYear()));
        Course b = courses.save(new Course(first, "B", time.today().getYear()));
        LocalDateTime start = time.now().plusDays(5).withHour(19).withMinute(0).withSecond(0).withNano(0);
        Event meeting = new Event("reunion-1a", "Reunión de apoderados", EventKind.PARENT_MEETING, start, start.plusHours(1));
        meeting.targetCourses(Set.of(a));
        meeting.setLocation("Sala 12");
        meeting.openRegistration(null, false, null);
        meeting.publish();
        events.save(meeting);
        em.flush();

        mvc.perform(get("/calendario/reuniones"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1° Básico A")))
                .andExpect(content().string(not(containsString("1° Básico B"))))
                .andExpect(content().string(containsString("Sala 12")));

        mvc.perform(publicForm("/calendario/reunion-1a/inscripcion").param("name", "Rosa").param("email", "rosa@correo.cl")
                        .param("attendees", "1").param("courseId", String.valueOf(b.getId())).param("consent", "true"))
                .andExpect(content().string(containsString("Elige el curso")));
        mvc.perform(publicForm("/calendario/reunion-1a/inscripcion").param("name", "Rosa").param("email", "rosa@correo.cl")
                        .param("attendees", "1").param("courseId", String.valueOf(a.getId())).param("studentName", "Tomás")
                        .param("consent", "true"))
                .andExpect(flash().attribute("notice", containsString("confirmada")));
        assertThat(byEmail("rosa@correo.cl").getCourse()).isEqualTo(a);
    }

    @Test
    void registrationNeedsTheEventsModule() throws Exception {
        openHouse.openRegistration(10, false, null);
        schools.findSingleton().orElseThrow().disableFeature(Feature.EVENTS);
        mvc.perform(get("/calendario/puertas-abiertas")).andExpect(content().string(not(containsString("id=\"inscripcion\""))));
        mvc.perform(register("rosa@correo.cl", 1).param("consent", "true")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/events/" + openHouse.getId() + "/registrations").with(as(editor))).andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder register(String email, int attendees) {
        return publicForm("/calendario/puertas-abiertas/inscripcion").param("name", "Familia " + email)
                .param("email", email).param("attendees", String.valueOf(attendees));
    }

    private EventRegistration byEmail(String email) {
        return registrations.findAll().stream().filter(r -> r.getEmail().equals(email)).findFirst().orElseThrow();
    }

    private String manageLinkFor(String email) {
        OutgoingMail mail = mails().stream().filter(m -> m.to().equals(email)).findFirst().orElseThrow();
        Matcher matcher = Pattern.compile("https?://[^/\\s]+(/inscripciones/\\S+)").matcher(mail.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
