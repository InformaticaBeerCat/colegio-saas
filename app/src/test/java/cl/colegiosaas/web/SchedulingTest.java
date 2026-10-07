package cl.colegiosaas.web;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.scheduling.AgendaConfigService;
import cl.colegiosaas.scheduling.Appointment;
import cl.colegiosaas.scheduling.AppointmentAudience;
import cl.colegiosaas.scheduling.AppointmentRepository;
import cl.colegiosaas.scheduling.AppointmentStatus;
import cl.colegiosaas.scheduling.AppointmentType;
import cl.colegiosaas.scheduling.BookingService;
import cl.colegiosaas.scheduling.SlotFinder;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.support.WebTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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

/** Agenda: tipos, disponibilidad, feriados, reserva, recordatorio, enlace seguro y panel (AGE-01..05, AGE-10). */
class SchedulingTest extends WebTestSupport {

    @Autowired LegalTextService legalTexts;
    @Autowired AgendaConfigService config;
    @Autowired BookingService booking;
    @Autowired SlotFinder slots;
    @Autowired AppointmentRepository appointments;
    @Autowired SchoolTime time;
    @Autowired EntityManager em;

    UserAccount admin;
    UserAccount ana;
    UserAccount pedro;
    AppointmentType visit;
    /** Un martes con al menos tres días de anticipación. */
    LocalDate tuesday;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        ana = activeUser("ana.admision@colegio.cl", Role.SCHEDULE_MANAGER);
        pedro = activeUser("pedro.inspectoria@colegio.cl", Role.SCHEDULE_MANAGER);
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_SCHEDULING, admin.getId());
        tuesday = time.today().plusDays(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        visit = config.saveType(null, new AgendaConfigService.TypeDraft("Visita guiada", "Recorre el colegio", 45, 15,
                AppointmentAudience.PROSPECTIVE_FAMILY, true, false, true, Set.of(ana.getId())));
        config.addRule(ana.getId(), Set.of(DayOfWeek.TUESDAY), LocalTime.of(9, 0), LocalTime.of(11, 0), null, null, null);
    }

    @Test
    void slotsFollowTheWeeklyWindowDurationAndBuffer() {
        List<SlotFinder.Slot> tuesdaySlots = slotsOn(tuesday);
        assertThat(tuesdaySlots).extracting(s -> s.start().toLocalTime()).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertThat(tuesdaySlots).allMatch(s -> s.hostName().equals("ana.admision"));
        assertThat(slots.available(visit, null)).allMatch(s -> s.start().getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    void holidaysBlocksAndTakenHoursAreNotOffered() {
        config.addHoliday(tuesday, "Feriado de prueba", false);
        assertThat(slotsOn(tuesday)).isEmpty();

        LocalDate next = tuesday.plusWeeks(1);
        config.addBlock(ana.getId(), next.atTime(8, 0), next.atTime(9, 30), "Licencia");
        assertThat(slotsOn(next)).extracting(s -> s.start().toLocalTime()).containsExactly(LocalTime.of(10, 0));

        LocalDate third = tuesday.plusWeeks(2);
        config.addBlock(null, third.atStartOfDay(), third.plusDays(1).atStartOfDay(), "Jornada de reflexión");
        assertThat(slotsOn(third)).isEmpty();
    }

    @Test
    void aFamilyBooksGetsTheIcsAndCanRescheduleOrCancelWithItsLink() throws Exception {
        mvc.perform(get("/agenda")).andExpect(content().string(containsString("Visita guiada")));
        mvc.perform(get("/agenda/" + visit.getId()))
                .andExpect(content().string(containsString("funcionario=" + ana.getId())))
                .andExpect(content().string(containsString("inicio=" + tuesday + "T09:00")));
        mvc.perform(get("/agenda/" + visit.getId() + "/reservar").param("funcionario", String.valueOf(ana.getId()))
                        .param("inicio", tuesday + "T09:00"))
                .andExpect(content().string(containsString("Confirma tu cita")))
                .andExpect(content().string(containsString("Aviso de privacidad: agenda de citas")));

        mvc.perform(book(tuesday.atTime(9, 0), "rosa@correo.cl").param("consent", "false"))
                .andExpect(content().string(containsString("marca la casilla")));
        mvc.perform(book(tuesday.atTime(9, 0), "rosa@correo.cl").param("consent", "true"))
                .andExpect(redirectedUrl("/agenda/confirmada"));
        Appointment appointment = appointments.findAll().getFirst();
        assertThat(appointment.getHost()).isEqualTo(ana);
        assertThat(appointment.getConsent().getPurpose()).isEqualTo(ConsentPurpose.SCHEDULING);
        assertThat(appointment.getStudentName()).isEqualTo("Tomás");
        assertThat(mails()).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("ana.admision@colegio.cl");
            assertThat(m.subject()).startsWith("Nueva cita: Visita guiada");
        });

        // La hora ya no se ofrece y no se puede tomar dos veces.
        assertThat(slotsOn(tuesday)).extracting(s -> s.start().toLocalTime()).containsExactly(LocalTime.of(10, 0));
        mvc.perform(book(tuesday.atTime(9, 0), "luis@correo.cl").param("consent", "true"))
                .andExpect(content().string(containsString("Esa hora ya no está disponible")));
        mvc.perform(book(tuesday.atTime(9, 20), "luis@correo.cl").param("consent", "true"))
                .andExpect(content().string(containsString("Esa hora ya no está disponible")));

        String link = manageLink("rosa@correo.cl");
        mvc.perform(get(link))
                .andExpect(content().string(containsString("Confirmada")))
                .andExpect(content().string(containsString("noindex")));
        String ics = mvc.perform(get(link + "/cita.ics"))
                .andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andReturn().getResponse().getContentAsString();
        assertThat(ics).contains("BEGIN:VEVENT", "SUMMARY:Visita guiada", "BEGIN:VALARM", "UID:cita-" + appointment.getId() + "@");

        mvc.perform(get(link + "/reprogramar")).andExpect(content().string(containsString(tuesday + "T10:00")));
        mvc.perform(post(link + "/reprogramar").param("inicio", tuesday + "T10:00").with(csrf()))
                .andExpect(redirectedUrl(link));
        assertThat(appointment.getStartsAt()).isEqualTo(tuesday.atTime(10, 0));
        assertThat(mails()).anySatisfy(m -> assertThat(m.subject()).startsWith("Cita reprogramada"));
        assertThat(slotsOn(tuesday)).extracting(s -> s.start().toLocalTime()).containsExactly(LocalTime.of(9, 0));

        mvc.perform(post(link + "/cancelar").param("reason", "Viaje").with(csrf())).andExpect(redirectedUrl(link));
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(slotsOn(tuesday)).hasSize(2);
        mvc.perform(post(link + "/cancelar").with(csrf())).andExpect(flash().attribute("problem", containsString("ya no está vigente")));
        mvc.perform(get("/citas/token-falso")).andExpect(status().isNotFound());
    }

    @Test
    void remindersAreSentOnceForAppointmentsInTheNextDay() {
        Appointment.Booking soon = bookDirectly(LocalDateTime.of(tuesday, LocalTime.of(9, 0)));
        assertThat(booking.sendReminders()).isZero();

        // La cita queda dentro de las próximas 24 horas.
        soon.appointment().reschedule(time.now().plusHours(5).withSecond(0).withNano(0));
        assertThat(booking.sendReminders()).isEqualTo(1);
        assertThat(mails()).anySatisfy(m -> assertThat(m.subject()).startsWith("Recordatorio: Visita guiada"));
        assertThat(booking.sendReminders()).isZero();
    }

    @Test
    void managersSeeOnlyTheirOwnAgendaAndSchoolAdminsSeeEveryone() throws Exception {
        Appointment appointment = bookDirectly(tuesday.atTime(9, 0)).appointment();
        em.flush();

        mvc.perform(get("/admin/scheduling").param("desde", tuesday.toString()).with(as(ana)))
                .andExpect(content().string(containsString("Rosa Muñoz")));
        mvc.perform(get("/admin/scheduling").param("desde", tuesday.toString()).param("host", String.valueOf(ana.getId())).with(as(pedro)))
                .andExpect(content().string(not(containsString("Rosa Muñoz"))));
        mvc.perform(get("/admin/scheduling/appointments/" + appointment.getId()).with(as(pedro))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/scheduling").param("desde", tuesday.toString()).with(as(admin)))
                .andExpect(content().string(containsString("Rosa Muñoz")));
        mvc.perform(get("/admin/scheduling/appointments/" + appointment.getId()).with(as(ana)))
                .andExpect(content().string(containsString("rosa@correo.cl")));

        mvc.perform(post("/admin/scheduling/appointments/" + appointment.getId() + "/cancel").param("reason", "Licencia médica")
                        .with(as(ana)).with(csrf()))
                .andExpect(flash().attribute("notice", containsString("la familia recibió el aviso")));
        assertThat(mails()).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("rosa@correo.cl");
            assertThat(m.body()).contains("Licencia médica", "/agenda/" + visit.getId());
        });

        mvc.perform(get("/admin/scheduling/types").with(as(ana))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/scheduling/holidays").with(as(ana))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/scheduling/availability/blocks").param("wholeSchool", "true")
                        .param("start", tuesday + "T08:00").param("end", tuesday + "T18:00").with(as(ana)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("Solo quien administra")));
    }

    @Test
    void managersEditTheirOwnAvailability() throws Exception {
        mvc.perform(post("/admin/scheduling/availability/rules").param("day", "WEDNESDAY", "THURSDAY")
                        .param("start", "15:00").param("end", "14:00").with(as(pedro)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("posterior")));
        mvc.perform(post("/admin/scheduling/availability/rules").param("day", "WEDNESDAY", "THURSDAY")
                        .param("start", "15:00").param("end", "17:00").with(as(pedro)).with(csrf()))
                .andExpect(flash().attribute("notice", "Disponibilidad agregada"));
        assertThat(config.rulesOf(pedro.getId())).hasSize(2);
        // Pedro no puede tocar el horario de Ana aunque lo pida por parámetro.
        mvc.perform(post("/admin/scheduling/availability/rules").param("host", String.valueOf(ana.getId())).param("day", "MONDAY")
                .param("start", "08:00").param("end", "09:00").with(as(pedro)).with(csrf()));
        assertThat(config.rulesOf(ana.getId())).hasSize(1);
        mvc.perform(get("/admin/scheduling/availability").with(as(pedro)))
                .andExpect(content().string(containsString("Miércoles")))
                .andExpect(content().string(containsString("15:00–17:00")));
    }

    @Test
    void typesNeedSomeoneToAttendThemAndCannotBeDeletedWithAppointments() throws Exception {
        mvc.perform(post("/admin/scheduling/types").param("name", "Entrevista").param("durationMinutes", "30")
                        .param("audience", "GUARDIAN").param("inPerson", "true").param("active", "true").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("al menos un funcionario")));
        mvc.perform(post("/admin/scheduling/types").param("name", "Entrevista profesor jefe").param("durationMinutes", "30")
                        .param("audience", "GUARDIAN").param("inPerson", "true").param("online", "true").param("active", "true")
                        .param("hostIds", String.valueOf(pedro.getId())).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Tipo de cita creado"));
        mvc.perform(post("/admin/scheduling/types").param("name", "Sin agenda").param("durationMinutes", "30")
                        .param("audience", "ANYONE").param("inPerson", "true").param("active", "true")
                        .param("hostIds", String.valueOf(admin.getId())).with(as(admin)).with(csrf()));

        bookDirectly(tuesday.atTime(9, 0));
        em.flush();
        mvc.perform(post("/admin/scheduling/types/" + visit.getId() + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("desactívalo")));
    }

    @Test
    void holidaysCanBeAddedByHandOnlyOncePerDay() throws Exception {
        mvc.perform(post("/admin/scheduling/holidays").param("date", tuesday.toString()).param("name", "Feriado regional")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Feriado agregado"));
        mvc.perform(post("/admin/scheduling/holidays").param("date", tuesday.toString()).param("name", "Otro")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", "Ya hay un feriado ese día"));
        mvc.perform(get("/admin/scheduling/holidays").param("anio", String.valueOf(tuesday.getYear())).with(as(admin)))
                .andExpect(content().string(containsString("Feriado regional")));
    }

    // --- Apoyo ---

    private List<SlotFinder.Slot> slotsOn(LocalDate day) {
        AppointmentType type = config.type(visit.getId());
        return slots.available(type, null, day, day);
    }

    private MockHttpServletRequestBuilder book(LocalDateTime start, String email) {
        return publicForm("/agenda/" + visit.getId() + "/reservar").param("funcionario", String.valueOf(ana.getId()))
                .param("inicio", start.toString()).param("name", "Rosa Muñoz").param("email", email).param("studentName", "Tomás");
    }

    private Appointment.Booking bookDirectly(LocalDateTime start) {
        BookingService.Booked booked = booking.book(visit.getId(), new BookingService.Request(ana.getId(), start, null, "Rosa Muñoz",
                "rosa@correo.cl", null, null, true), new cl.colegiosaas.privacy.RequestOrigin("/agenda", "127.0.0.1", "JUnit"));
        return new Appointment.Booking(booked.appointment(), booked.manageUrl());
    }

    private String manageLink(String email) {
        OutgoingMail mail = mails().stream().filter(m -> m.to().equals(email)).findFirst().orElseThrow();
        Matcher matcher = Pattern.compile("https?://[^/\\s]+(/citas/[A-Za-z0-9_-]+)\\s").matcher(mail.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
