package cl.colegiosaas.web;

import cl.colegiosaas.admissions.AdmissionMode;
import cl.colegiosaas.admissions.AdmissionService;
import cl.colegiosaas.admissions.Prospect;
import cl.colegiosaas.admissions.ProspectRepository;
import cl.colegiosaas.admissions.ProspectStage;
import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.calendar.EventRepository;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.ConsentRecordRepository;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.scheduling.AgendaConfigService;
import cl.colegiosaas.scheduling.AppointmentAudience;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admisión SAE: página, hitos, vacantes, edades configurables, visitas y registro de interés (ADM-01..03, 07, 08). */
class AdmissionsTest extends WebTestSupport {

    @Autowired AdmissionService admissions;
    @Autowired LegalTextService legalTexts;
    @Autowired GradeLevelRepository levels;
    @Autowired ProspectRepository prospects;
    @Autowired ConsentRecordRepository consents;
    @Autowired EventRepository events;
    @Autowired AgendaConfigService agenda;
    @Autowired SchoolTime time;
    @Autowired BlindIndex index;

    UserAccount admin;
    UserAccount editor;
    GradeLevel preKinder;
    GradeLevel kinder;
    int year;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
        preKinder = levels.save(new GradeLevel("Pre-kínder", EducationStage.EARLY_CHILDHOOD, null, 1));
        kinder = levels.save(new GradeLevel("Kínder", EducationStage.EARLY_CHILDHOOD, null, 2));
        year = admissions.current().getProcessYear();
    }

    @Test
    void theInstallationStartsInSaeModeForNextYear() {
        assertThat(admissions.current().getMode()).isEqualTo(AdmissionMode.SAE);
        assertThat(year).isEqualTo(time.today().getYear() + 1);
    }

    @Test
    void thePageShowsMilestonesOpenLevelsWithBirthDatesVacanciesAndVisits() throws Exception {
        mvc.perform(post("/admin/admissions/levels/" + preKinder.getId()).param("open", "true")
                        .param("bornFrom", (year - 4) + "-04-01").param("bornUntil", (year - 3) + "-03-31").param("seats", "30")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Nivel guardado"));
        mvc.perform(post("/admin/admissions/levels/" + kinder.getId()).param("open", "false").param("seats", "2")
                .with(as(admin)).with(csrf()));
        mvc.perform(post("/admin/admissions/levels/" + kinder.getId()).param("open", "true")
                        .param("bornFrom", "2022-04-01").param("bornUntil", "2021-03-31").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("no puede ser anterior")));
        mvc.perform(post("/admin/admissions/milestones").param("name", "Postulación SAE, período principal")
                        .param("startsOn", (year - 1) + "-08-12").param("endsOn", (year - 1) + "-08-30")
                        .param("linkUrl", "https://www.sistemadeadmisionescolar.cl/").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Hito agregado"));
        mvc.perform(post("/admin/admissions/milestones").param("name", "Malo").param("startsOn", (year - 1) + "-08-12")
                        .param("linkUrl", "javascript:alert(1)").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("Enlace no permitido")));
        mvc.perform(post("/admin/admissions/settings").param("mode", "SAE").param("processYear", String.valueOf(year))
                .param("introText", "<p>Proyecto educativo <strong>laico</strong></p><script>x()</script>").param("showVacancies", "true")
                .with(as(admin)).with(csrf()));

        LocalDateTime start = time.now().plusDays(20).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Event openHouse = new Event("puertas-abiertas", "Jornada de puertas abiertas", EventKind.OPEN_HOUSE, start, start.plusHours(2));
        openHouse.publish();
        events.save(openHouse);
        UserAccount ana = activeUser("ana@colegio.cl", Role.SCHEDULE_MANAGER);
        agenda.saveType(null, new AgendaConfigService.TypeDraft("Visita guiada", null, 45, 0, AppointmentAudience.PROSPECTIVE_FAMILY,
                true, false, true, Set.of(ana.getId())));
        agenda.saveType(null, new AgendaConfigService.TypeDraft("Entrevista apoderados", null, 30, 0, AppointmentAudience.GUARDIAN,
                true, false, true, Set.of(ana.getId())));

        mvc.perform(get("/admision"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admisión " + year)))
                .andExpect(content().string(containsString("Sistema de Admisión Escolar")))
                .andExpect(content().string(containsString("href=\"https://www.sistemadeadmisionescolar.cl/\"")))
                .andExpect(content().string(containsString("Postulación SAE, período principal")))
                .andExpect(content().string(containsString("Pre-kínder")))
                .andExpect(content().string(containsString("Entre el 1 de abril de " + (year - 4))))
                .andExpect(content().string(containsString("<td>30</td>")))
                .andExpect(content().string(not(containsString("Kínder</th>"))))
                .andExpect(content().string(containsString("<strong>laico</strong>")))
                .andExpect(content().string(not(containsString("<script>x()"))))
                .andExpect(content().string(containsString("Visita guiada")))
                .andExpect(content().string(not(containsString("Entrevista apoderados"))))
                .andExpect(content().string(containsString("Jornada de puertas abiertas")));
    }

    @Test
    void ownAdmissionsNeedTheAdmissionsProModule() throws Exception {
        mvc.perform(post("/admin/admissions/settings").param("mode", "OWN").param("processYear", String.valueOf(year))
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("Admisión Pro")));
        mvc.perform(post("/admin/admissions/settings").param("mode", "SAE").param("processYear", String.valueOf(year + 5))
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("año del proceso")));
        mvc.perform(get("/admin/admissions").with(as(editor))).andExpect(status().isForbidden());
    }

    @Test
    void familiesRegisterInterestWithSeparateConsentsAndCampaignData() throws Exception {
        admissions.saveLevel(preKinder.getId(), true, null, null, 30);
        mvc.perform(get("/admision")).andExpect(content().string(containsString("El registro en línea se habilitará pronto")));
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_ADMISSIONS, admin.getId());

        mvc.perform(get("/admision").param("utm_source", "instagram").param("utm_campaign", "admision-2027"))
                .andExpect(content().string(containsString("value=\"instagram\"")))
                .andExpect(content().string(containsString("name=\"followUp\"")));

        mvc.perform(interest().param("gradeLevelId", String.valueOf(kinder.getId())).param("consent", "true"))
                .andExpect(content().string(containsString("no recibe postulantes")));
        mvc.perform(interest().param("gradeLevelId", String.valueOf(preKinder.getId())))
                .andExpect(content().string(containsString("marca la casilla")));
        mvc.perform(interest().param("gradeLevelId", String.valueOf(preKinder.getId())).param("consent", "true")
                        .param("utmSource", "instagram").param("utmCampaign", "admision-2027"))
                .andExpect(redirectedUrl("/admision#interes"))
                .andExpect(flash().attribute("notice", containsString("Registramos tu interés")));

        Prospect prospect = prospects.findAll().getFirst();
        assertThat(prospect.getGradeLevel()).isEqualTo(preKinder);
        assertThat(prospect.getEntryYear()).isEqualTo(year);
        assertThat(prospect.getUtmSource()).isEqualTo("instagram");
        assertThat(prospect.acceptsFollowUp()).isFalse();
        assertThat(prospect.getRetainUntil()).isEqualTo(time.today().plusDays(365));
        assertThat(consents.findBySubjectEmailHashOrderByCreatedAtDesc(index.of("familia@correo.cl")))
                .extracting(c -> c.getPurpose() + ":" + c.isGranted())
                .containsExactlyInAnyOrder("ADMISSIONS:true", "ADMISSIONS_FOLLOW_UP:false");
        assertThat(mails()).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("familia@correo.cl");
            assertThat(m.body()).contains("Sistema de Admisión Escolar", "/admision");
        });
        assertThat(mails()).anySatisfy(m -> assertThat(m.to()).isEqualTo("directora@colegio.cl"));

        mvc.perform(publicForm("/admision/interes").param("name", "Otra familia").param("email", "otra@correo.cl")
                        .param("consent", "true").param("followUp", "true"))
                .andExpect(redirectedUrl("/admision#interes"));
        assertThat(prospects.findByEmailHash(index.of("otra@correo.cl")).getFirst().acceptsFollowUp()).isTrue();

        mvc.perform(get("/admin/admissions/prospects").with(as(admin)))
                .andExpect(content().string(containsString("instagram")))
                .andExpect(content().string(not(containsString("familia@correo.cl"))));
        mvc.perform(get("/admin/admissions/prospects/" + prospect.getId()).with(as(admin)))
                .andExpect(content().string(containsString("familia@correo.cl")));
        mvc.perform(post("/admin/admissions/prospects/" + prospect.getId() + "/stage").param("stage", "VISITED")
                .with(as(admin)).with(csrf()));
        assertThat(prospect.getStage()).isEqualTo(ProspectStage.VISITED);
        mvc.perform(post("/admin/admissions/prospects/" + prospect.getId() + "/stage").param("stage", "INTERESTED")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("no retrocede")));
        mvc.perform(post("/admin/admissions/prospects/" + prospect.getId() + "/delete").with(as(admin)).with(csrf()));
        assertThat(prospects.findById(prospect.getId())).isEmpty();
        assertThatThrownBy(() -> admissions.prospect(prospect.getId())).isInstanceOf(RuntimeException.class);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder interest() {
        return publicForm("/admision/interes").param("name", "Familia Soto").param("email", "familia@correo.cl")
                .param("phone", "+56912345678");
    }
}
