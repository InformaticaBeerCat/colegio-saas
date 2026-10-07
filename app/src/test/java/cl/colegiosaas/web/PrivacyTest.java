package cl.colegiosaas.web;

import cl.colegiosaas.admissions.Prospect;
import cl.colegiosaas.admissions.ProspectRepository;
import cl.colegiosaas.admissions.ProspectSource;
import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditLogRepository;
import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.calendar.EventRegistration;
import cl.colegiosaas.calendar.EventRegistrationRepository;
import cl.colegiosaas.calendar.EventRepository;
import cl.colegiosaas.consent.Student;
import cl.colegiosaas.consent.StudentRepository;
import cl.colegiosaas.contact.ContactArea;
import cl.colegiosaas.contact.ContactAreaRepository;
import cl.colegiosaas.contact.Inquiry;
import cl.colegiosaas.contact.InquiryRepository;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.ConsentRecordRepository;
import cl.colegiosaas.privacy.ConsentService;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.privacy.DataSubjectRequest;
import cl.colegiosaas.privacy.DataSubjectRequestRepository;
import cl.colegiosaas.privacy.DataSubjectRequestStatus;
import cl.colegiosaas.privacy.IncidentService;
import cl.colegiosaas.privacy.IncidentSeverity;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.privacy.RetentionAction;
import cl.colegiosaas.privacy.RetentionCategory;
import cl.colegiosaas.privacy.RetentionPolicyRepository;
import cl.colegiosaas.privacy.RetentionService;
import cl.colegiosaas.privacy.SecurityIncident;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.web.RuleViolation;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

/** Privacidad bajo la Ley 21.719: textos versionados, consentimientos, cookies, derechos, retención y brechas (fase 6). */
class PrivacyTest extends WebTestSupport {

    @Autowired LegalTextService legalTexts;
    @Autowired ConsentService consents;
    @Autowired ConsentRecordRepository consentRecords;
    @Autowired DataSubjectRequestRepository requests;
    @Autowired RetentionService retention;
    @Autowired RetentionPolicyRepository policies;
    @Autowired IncidentService incidents;
    @Autowired AuditLogRepository auditLog;
    @Autowired ContactAreaRepository areas;
    @Autowired InquiryRepository inquiries;
    @Autowired ProspectRepository prospects;
    @Autowired EventRepository calendar;
    @Autowired EventRegistrationRepository registrations;
    @Autowired StudentRepository students;
    @Autowired GradeLevelRepository levels;
    @Autowired CourseRepository courses;
    @Autowired SchoolTime time;
    @Autowired BlindIndex index;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    UserAccount superAdmin;
    UserAccount admin;
    UserAccount editor;
    RequestOrigin origin = new RequestOrigin("/contacto", "200.1.2.3", "JUnit");

    @BeforeEach
    void setUp() {
        superAdmin = install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
    }

    // --- DOC-06: textos legales versionados -------------------------------------------------------------

    @Test
    void legalTextsStartFromATemplateWithTheSchoolDataAndPublishedVersionsAreImmutable() throws Exception {
        mvc.perform(post("/admin/legal/PRIVACY_POLICY/draft").param("fromTemplate", "true").with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        LegalText draft = legalTexts.overview().stream().filter(o -> o.kind() == LegalTextKind.PRIVACY_POLICY)
                .findFirst().orElseThrow().draft();
        assertThat(draft.getContent()).contains("Colegio San José", "8485-1", "contacto@colegio.cl", "/privacidad/derechos");

        mvc.perform(post("/admin/legal/texts/" + draft.getId()).param("title", "Política de privacidad")
                        .param("content", "<h2>Quiénes somos</h2><p>Primera versión</p><script>alert(1)</script>")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Borrador guardado"));
        assertThat(legalTexts.get(draft.getId()).getContent()).contains("Primera versión").doesNotContain("script");

        mvc.perform(get("/privacidad/politica")).andExpect(status().isNotFound());
        mvc.perform(post("/admin/legal/texts/" + draft.getId() + "/publish").with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/legal"));
        mvc.perform(get("/privacidad/politica"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Primera versión")))
                .andExpect(content().string(containsString("Versión 1, vigente desde")));

        // Publicada no se edita: el cambio es una versión nueva, que parte de la vigente.
        assertThatThrownBy(() -> legalTexts.edit(draft.getId(), "Otro", "<p>Cambio</p>")).isInstanceOf(RuleViolation.class);
        LegalText second = legalTexts.openDraft(LegalTextKind.PRIVACY_POLICY, false);
        assertThat(second.getVersionNumber()).isEqualTo(2);
        assertThat(second.getContent()).contains("Primera versión");
        legalTexts.edit(second.getId(), "Política de privacidad", "<p>Segunda versión</p>");
        legalTexts.publish(second.getId(), admin.getId());

        mvc.perform(get("/privacidad/politica")).andExpect(content().string(containsString("Segunda versión")));
        mvc.perform(get("/privacidad/politica/v1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Primera versión")))
                .andExpect(content().string(containsString("Estás viendo una versión anterior")));
        mvc.perform(get("/privacidad/politica/v3")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/legal/PRIVACY_POLICY/history").with(as(admin)))
                .andExpect(content().string(containsString("v2: Política de privacidad")))
                .andExpect(content().string(containsString("directora")));
    }

    @Test
    void onlyPrivacyManagersAdministerLegalTextsAndPersonalData() throws Exception {
        for (String url : List.of("/admin/legal", "/admin/privacy", "/admin/privacy/requests", "/admin/privacy/people",
                "/admin/privacy/retention", "/admin/privacy/incidents")) {
            mvc.perform(get(url).with(as(editor))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(as(admin))).andExpect(status().isOk());
        }
    }

    @Test
    void theDashboardWarnsWhileTheMandatoryTextsAreMissing() throws Exception {
        mvc.perform(get("/admin").with(as(admin)))
                .andExpect(content().string(containsString("Falta publicar la política de privacidad")));
        legalTexts.publishFromTemplate(LegalTextKind.PRIVACY_POLICY, admin.getId());
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_DATA_REQUESTS, admin.getId());
        mvc.perform(get("/admin").with(as(admin)))
                .andExpect(content().string(not(containsString("Falta publicar la política de privacidad"))));
        mvc.perform(get("/admin").with(as(editor)))
                .andExpect(content().string(not(containsString("Falta publicar"))));
    }

    @Test
    void noticesQuoteTheRetentionPeriodOfTheirForm() {
        LegalText notice = legalTexts.publishFromTemplate(LegalTextKind.NOTICE_CONTACT, admin.getId());
        assertThat(notice.getContent()).contains("2 años");
    }

    // --- PRV-02, PRV-04: consentimientos ----------------------------------------------------------------

    @Test
    void consentsRecordTheExactVersionOfTheNoticeAndRequireItToBePublished() {
        DataSubject ana = new DataSubject("Ana Pérez", "ana@correo.cl");
        assertThatThrownBy(() -> consents.record(ana, ConsentPurpose.NEWSLETTER, true, origin))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("aviso de privacidad");

        LegalText v1 = legalTexts.publishFromTemplate(LegalTextKind.NOTICE_NEWSLETTER, admin.getId());
        ConsentRecord yes = consents.record(ana, ConsentPurpose.NEWSLETTER, true, origin);
        ConsentRecord no = consents.record(new DataSubject("Luis", "luis@correo.cl"), ConsentPurpose.NEWSLETTER, false, origin);
        assertThat(yes.getLegalText()).isEqualTo(v1);
        assertThat(yes.isActive()).isTrue();
        assertThat(no.isActive()).isFalse();
        assertThat(no.getSubjectEmailHash()).isEqualTo(index.of("LUIS@correo.cl "));

        LegalText v2 = legalTexts.openDraft(LegalTextKind.NOTICE_NEWSLETTER, false);
        legalTexts.publish(v2.getId(), admin.getId());
        assertThat(consents.record(ana, ConsentPurpose.NEWSLETTER, true, origin).getLegalText().getVersionNumber()).isEqualTo(2);
        assertThat(yes.getLegalText().getVersionNumber()).isEqualTo(1);

        consents.withdraw(yes.getId());
        assertThat(yes.isActive()).isFalse();
    }

    // --- PRV-03: banner de cookies ------------------------------------------------------------------------

    @Test
    void withoutAnExternalAnalyticsToolThereIsNothingToConsentToAndNoBanner() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.COOKIE_POLICY, admin.getId());
        mvc.perform(get("/documentos"))
                .andExpect(content().string(not(containsString("class=\"cookie-banner\""))))
                .andExpect(content().string(not(containsString("analytics"))));
    }

    @Test
    void theFooterAlwaysLinksToPrivacyAndRights() throws Exception {
        mvc.perform(get("/documentos"))
                .andExpect(content().string(containsString("href=\"/privacidad\"")))
                .andExpect(content().string(containsString("href=\"/privacidad/derechos\"")));
    }

    // --- PRV-05: solicitudes de derechos ------------------------------------------------------------------

    @Test
    void theRightsFormIsOfferedOnlyWithItsNoticePublished() throws Exception {
        mvc.perform(get("/privacidad/derechos"))
                .andExpect(content().string(containsString("Este formulario se habilitará pronto")))
                .andExpect(content().string(containsString("contacto@colegio.cl")));
        mvc.perform(rightsRequest("ERASURE").param("noticeRead", "true").with(csrf()))
                .andExpect(content().string(containsString("falta publicar su aviso")));
        assertThat(requests.count()).isZero();
    }

    @Test
    void aRequestGetsATrackingCodeADeadlineAndIsAnsweredFromTheInbox() throws Exception {
        LegalText notice = legalTexts.publishFromTemplate(LegalTextKind.NOTICE_DATA_REQUESTS, admin.getId());
        mvc.perform(get("/privacidad/derechos"))
                .andExpect(content().string(containsString("Cómo usamos tus datos en este formulario")))
                .andExpect(content().string(containsString("/privacidad/aviso-derechos/v1")));

        mvc.perform(rightsRequest("ACCESS").with(csrf()))
                .andExpect(content().string(containsString("Confirma que leíste el aviso")));

        mvc.perform(rightsRequest("ACCESS").param("noticeRead", "true").param("onBehalfOfMinor", "true").with(csrf()))
                .andExpect(redirectedUrl("/privacidad/derechos/estado"))
                .andExpect(flash().attributeExists("submitted"));

        DataSubjectRequest request = requests.findAll().getFirst();
        assertThat(request.getDueOn()).isEqualTo(time.today().plusDays(30));
        assertThat(request.isOnBehalfOfMinor()).isTrue();
        ConsentRecord evidence = consentRecords.findBySubjectEmailHashOrderByCreatedAtDesc(index.of("maria@correo.cl")).getFirst();
        assertThat(evidence.getPurpose()).isEqualTo(ConsentPurpose.DATA_REQUEST);
        assertThat(evidence.getLegalText()).isEqualTo(notice);
        assertThat(evidence.getIpAddress()).isNotNull();

        List<OutgoingMail> sent = mails();
        assertThat(sent).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("maria@correo.cl");
            assertThat(m.body()).contains(request.getTrackingCode(), "/privacidad/derechos/estado?codigo=");
        });
        assertThat(sent).extracting(OutgoingMail::to).contains("super@colegio.cl", "directora@colegio.cl")
                .doesNotContain("comunicaciones@colegio.cl");

        mvc.perform(get("/privacidad/derechos/estado").param("codigo", request.getTrackingCode().toLowerCase()))
                .andExpect(content().string(containsString("Recibida")))
                .andExpect(content().string(not(containsString("maria@correo.cl"))))
                .andExpect(content().string(containsString("noindex")));
        mvc.perform(get("/privacidad/derechos/estado").param("codigo", "D-NOEXISTE"))
                .andExpect(content().string(containsString("No encontramos")));

        mvc.perform(get("/admin/privacy/requests").with(as(admin)))
                .andExpect(content().string(containsString(request.getTrackingCode())));
        mvc.perform(get("/admin/privacy/requests/" + request.getId()).with(as(admin)))
                .andExpect(content().string(containsString("maria@correo.cl")));
        assertThat(auditLog.findByActionOrderByIdAsc(AuditAction.VIEW_PERSONAL_DATA))
                .anySatisfy(e -> assertThat(e.getEntityType()).isEqualTo("DataSubjectRequest"));

        mvc.perform(post("/admin/privacy/requests/" + request.getId() + "/start").with(as(admin)).with(csrf()));
        assertThat(request.getStatus()).isEqualTo(DataSubjectRequestStatus.IN_PROGRESS);
        mvc.perform(post("/admin/privacy/requests/" + request.getId() + "/complete").param("resolution", "")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("Describe qué se hizo")));
        mvc.perform(post("/admin/privacy/requests/" + request.getId() + "/complete")
                        .param("resolution", "Adjuntamos tus datos.").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", containsString("respondida")));
        assertThat(request.getStatus()).isEqualTo(DataSubjectRequestStatus.COMPLETED);
        assertThat(mails()).anySatisfy(m -> assertThat(m.body()).contains("Adjuntamos tus datos."));
        mvc.perform(post("/admin/privacy/requests/" + request.getId() + "/reject").param("reason", "Tarde")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("cerrada")));
    }

    @Test
    void botsFillingTheHoneypotGetTheSameAnswerButNothingIsStored() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_DATA_REQUESTS, admin.getId());
        mvc.perform(rightsRequest("ERASURE").param("noticeRead", "true").param("sitio_web", "http://spam.example").with(csrf()))
                .andExpect(redirectedUrl("/privacidad/derechos/estado"));
        assertThat(requests.count()).isZero();
        assertThat(mails()).isEmpty();
    }

    @Test
    void overdueRequestsAreFlagged() {
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_DATA_REQUESTS, admin.getId());
        DataSubjectRequest request = requests.save(new DataSubjectRequest(cl.colegiosaas.privacy.DataSubjectRight.ACCESS,
                new DataSubject("Pedro", "pedro@correo.cl"), false, null, time.today().minusDays(1), index));
        assertThat(request.isOverdue(time.today())).isTrue();
        assertThat(retentionSafeOverdueCount()).isEqualTo(1);
    }

    // --- PRV-07: exportar y suprimir a una persona --------------------------------------------------------

    @Test
    void everythingAboutAPersonIsFoundExportedAndErased() throws Exception {
        String email = "familia@correo.cl";
        publishAllNotices();
        Inquiry inquiry = inquiries.save(new Inquiry(area(), new DataSubject("Familia Soto", email), "+56912345678", "Matrícula",
                "¿Quedan vacantes?", consents.record(new DataSubject("Familia Soto", email), ConsentPurpose.CONTACT, true, origin), index));
        inquiry.addNote(superAdmin, "Llamar el lunes");
        Prospect prospect = prospects.save(new Prospect(new DataSubject("Familia Soto", email), null, null, 2027,
                ProspectSource.WEBSITE_FORM, consents.record(new DataSubject("Familia Soto", email), ConsentPurpose.ADMISSIONS, true, origin),
                time.today().plusYears(1), index));
        EventRegistration registration = registrations.save(EventRegistration.confirmed(openHouse(),
                new EventRegistration.Registrant(new DataSubject("Familia Soto", "FAMILIA@correo.cl"), null, 2, null, "Tomás"),
                consents.record(new DataSubject("Familia Soto", email), ConsentPurpose.EVENT_REGISTRATION, true, origin), index).registration());
        Student tomas = students.save(new Student("Tomás Soto", course(), email, index));
        ConsentRecord newsletter = consents.record(new DataSubject("Familia Soto", email), ConsentPurpose.NEWSLETTER, true, origin);
        DataSubjectRequest erasureRequest = requests.save(new DataSubjectRequest(cl.colegiosaas.privacy.DataSubjectRight.ERASURE,
                new DataSubject("Familia Soto", email), false, "Borren todo", time.today().plusDays(30), index));
        consents.record(new DataSubject("Otra", "otra@correo.cl"), ConsentPurpose.NEWSLETTER, true, origin);
        em.flush();

        mvc.perform(get("/admin/privacy/people").with(as(admin))).andExpect(content().string(containsString("Buscar")));
        mvc.perform(post("/admin/privacy/people").param("email", " Familia@Correo.cl ").with(as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("¿Quedan vacantes?")))
                .andExpect(content().string(containsString("Puertas abiertas")))
                .andExpect(content().string(containsString("Tomás Soto")))
                .andExpect(content().string(containsString("Borren todo")))
                .andExpect(content().string(not(containsString("otra@correo.cl"))));

        String json = mvc.perform(post("/admin/privacy/people/export").param("email", email).with(as(admin)).with(csrf()))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).contains("\"consultas\"", "¿Quedan vacantes?", "\"estudiantes\"", "Tomás Soto", "NEWSLETTER");
        assertThat(auditLog.findByActionOrderByIdAsc(AuditAction.EXPORT)).filteredOn(e -> "DataSubject".equals(e.getEntityType())).hasSize(1);

        mvc.perform(post("/admin/privacy/people/erase").param("email", email).param("confirmEmail", "otro@correo.cl")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("vuelve a escribir")));
        assertThat(inquiries.findById(inquiry.getId())).isPresent();

        mvc.perform(post("/admin/privacy/people/erase").param("email", email).param("confirmEmail", "FAMILIA@correo.cl")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", containsString("3 registro(s) borrados, 4 consentimiento(s) anonimizados, 1 estudiante(s)")));
        em.flush();
        em.clear();
        assertThat(inquiries.findById(inquiry.getId())).isEmpty();
        assertThat(prospects.findById(prospect.getId())).isEmpty();
        assertThat(registrations.findById(registration.getId())).isEmpty();
        Student kept = students.findById(tomas.getId()).orElseThrow();
        assertThat(kept.getGuardianEmail()).isNull();
        ConsentRecord anonymized = consentRecords.findById(newsletter.getId()).orElseThrow();
        assertThat(anonymized.isAnonymized()).isTrue();
        assertThat(anonymized.getSubjectName()).isNull();
        assertThat(anonymized.isActive()).isFalse();
        assertThat(anonymized.getLegalText().getKind()).isEqualTo(LegalTextKind.NOTICE_NEWSLETTER);
        assertThat(requests.findById(erasureRequest.getId()).orElseThrow().getRequesterEmail()).isEqualTo(email);
        assertThat(consentRecords.findBySubjectEmailHashOrderByCreatedAtDesc(index.of("otra@correo.cl"))).hasSize(1);
    }

    // --- PRV-06: retención ---------------------------------------------------------------------------------

    @Test
    void retentionAnonymizesOrDeletesWhatExpiredAndKeepsWhatIsStillInUse() {
        publishAllNotices();
        Inquiry old = inquiries.save(new Inquiry(area(), new DataSubject("Ana", "ana@correo.cl"), null, "Hola", "Mensaje antiguo",
                consents.record(new DataSubject("Ana", "ana@correo.cl"), ConsentPurpose.CONTACT, true, origin), index));
        Inquiry recent = inquiries.save(new Inquiry(area(), new DataSubject("Bea", "bea@correo.cl"), null, "Hola", "Mensaje nuevo",
                consents.record(new DataSubject("Bea", "bea@correo.cl"), ConsentPurpose.CONTACT, true, origin), index));
        ConsentRecord oldNewsletter = consents.record(new DataSubject("Ana", "ana@correo.cl"), ConsentPurpose.NEWSLETTER, true, origin);
        Student gone = students.save(new Student("Pedro Retirado", course(), null, index));
        gone.deactivate();
        Student current = students.save(new Student("Sofía Activa", course(), null, index));
        DataSubjectRequest answered = requests.save(new DataSubjectRequest(cl.colegiosaas.privacy.DataSubjectRight.ACCESS,
                new DataSubject("Ana", "ana@correo.cl"), false, "Mis datos", time.today(), index));
        answered.complete("Enviados");
        em.flush();

        Instant longAgo = Instant.now().minus(Duration.ofDays(2000));
        backdate("inquiry", old.getId(), longAgo);
        jdbc.update("update consent_record set created_at = ?", Timestamp.from(longAgo));
        backdate("student", gone.getId(), longAgo);
        backdate("student", current.getId(), longAgo);
        jdbc.update("update data_subject_request set resolved_at = ? where id = ?", Timestamp.from(longAgo), answered.getId());
        jdbc.update("insert into audit_log (occurred_at, actor_type, action, entity_type, entity_id) values (?, 'SYSTEM', 'UPDATE', 'Antiguo', '1')",
                Timestamp.from(longAgo));
        em.clear();

        var done = retention.apply();
        em.flush();
        em.clear();

        assertThat(done.get(RetentionCategory.INQUIRIES)).isEqualTo(1);
        Inquiry anonymized = inquiries.findById(old.getId()).orElseThrow();
        assertThat(anonymized.isAnonymized()).isTrue();
        assertThat(anonymized.getMessage()).isEqualTo("[anonimizado]");
        assertThat(anonymized.getNotes()).isEmpty();
        assertThat(inquiries.findById(recent.getId()).orElseThrow().getMessage()).isEqualTo("Mensaje nuevo");

        // Los de una sola vez se anonimizan; el boletín sigue vigente.
        assertThat(consentRecords.findById(oldNewsletter.getId()).orElseThrow().isAnonymized()).isFalse();
        assertThat(consentRecords.findById(old.getConsent().getId()).orElseThrow().isAnonymized()).isTrue();

        assertThat(students.findById(gone.getId())).isEmpty();
        assertThat(students.findById(current.getId())).isPresent();
        DataSubjectRequest kept = requests.findById(answered.getId()).orElseThrow();
        assertThat(kept.isAnonymized()).isTrue();
        assertThat(kept.getStatus()).isEqualTo(DataSubjectRequestStatus.COMPLETED);
        assertThat(auditLog.findAll()).noneMatch(e -> "Antiguo".equals(e.getEntityType()));
        assertThat(auditLog.findAll()).anyMatch(e -> e.getDetails() != null && e.getDetails().startsWith("Retención INQUIRIES"));

        // Una segunda pasada no vuelve a tocar lo anonimizado.
        assertThat(retention.apply().get(RetentionCategory.INQUIRIES)).isZero();
    }

    @Test
    void retentionPoliciesAreEditableWithinTheActionsEachCategoryAllows() throws Exception {
        long inquiriesPolicy = policies.findByDataCategory(RetentionCategory.INQUIRIES).orElseThrow().getId();
        long consentsPolicy = policies.findByDataCategory(RetentionCategory.CONSENT_RECORDS).orElseThrow().getId();

        mvc.perform(post("/admin/privacy/retention/" + inquiriesPolicy).param("days", "365").param("action", "DELETE")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Plazo guardado"));
        assertThat(policies.findById(inquiriesPolicy).orElseThrow().getAction()).isEqualTo(RetentionAction.DELETE);

        mvc.perform(post("/admin/privacy/retention/" + consentsPolicy).param("days", "365").param("action", "DELETE")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("no corresponde")));
        mvc.perform(post("/admin/privacy/retention/" + inquiriesPolicy).param("days", "0").param("action", "DELETE")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("entre 1 día")));

        mvc.perform(post("/admin/privacy/retention/run").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "No había datos vencidos"));
        mvc.perform(get("/admin/privacy/retention").with(as(admin)))
                .andExpect(content().string(containsString("Consultas del formulario de contacto")))
                .andExpect(content().string(containsString("(≈ 5 años)")));
    }

    // --- PRV-09: brechas -----------------------------------------------------------------------------------

    @Test
    void incidentsTrackTheNotificationDeadlineAndProposeTheNotices() throws Exception {
        LocalDateTime detected = time.now().minusHours(80).withSecond(0).withNano(0);
        mvc.perform(post("/admin/privacy/incidents").param("title", "Planilla de apoderados enviada a un grupo equivocado")
                        .param("detectedAt", detected.toString()).param("severity", "HIGH")
                        .param("affectedData", "nombres y teléfonos de apoderados de 3° básico").param("affectedSubjectsEstimate", "35")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", containsString("Incidente registrado")));
        SecurityIncident incident = incidents.list().getFirst();
        assertThat(mails()).extracting(OutgoingMail::to).contains("super@colegio.cl", "directora@colegio.cl");

        IncidentService.Clockwork clock = incidents.clockwork(incident);
        assertThat(clock.overdue()).isTrue();
        assertThat(clock.hoursElapsed()).isEqualTo(80);
        assertThat(incidents.authorityNotice(incident))
                .contains("Colegio San José (RBD 8485-1)", "nombres y teléfonos de apoderados", "35");

        mvc.perform(get("/admin/privacy/incidents/" + incident.getId()).with(as(admin)))
                .andExpect(content().string(containsString("Se superó la meta de 72 horas")))
                .andExpect(content().string(containsString("Borrador para las familias")));
        mvc.perform(get("/admin").with(as(admin)))
                .andExpect(content().string(containsString("1 incidente(s) de seguridad sin cerrar")));

        mvc.perform(post("/admin/privacy/incidents/" + incident.getId() + "/close").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("medidas tomadas")));
        mvc.perform(post("/admin/privacy/incidents/" + incident.getId() + "/contain").with(as(admin)).with(csrf()));
        mvc.perform(post("/admin/privacy/incidents/" + incident.getId() + "/authority")
                .param("at", time.now().minusHours(1).withSecond(0).withNano(0).toString()).with(as(admin)).with(csrf()));
        mvc.perform(post("/admin/privacy/incidents/" + incident.getId()).param("title", incident.getTitle())
                .param("detectedAt", detected.toString()).param("severity", "HIGH")
                .param("actionsTaken", "Se pidió borrar el mensaje y se cambió la lista de distribución.")
                .with(as(admin)).with(csrf()));
        mvc.perform(post("/admin/privacy/incidents/" + incident.getId() + "/close").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Incidente cerrado"));
        assertThat(incidents.openCount()).isZero();
        assertThat(incidents.clockwork(incident).hoursElapsed()).isEqualTo(79);

        assertThatThrownBy(() -> incidents.register(new IncidentService.Details("Futuro", time.now().plusDays(1),
                IncidentSeverity.LOW, null, null, null, null), admin.getId())).isInstanceOf(RuleViolation.class);
    }

    // --- Apoyo ----------------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rightsRequest(String right) {
        return post("/privacidad/derechos").param(cl.colegiosaas.shared.forms.FormGuard.STAMP, formGuard.stamp()).param("right", right).param("name", "María González")
                .param("email", "maria@correo.cl").param("details", "Quiero saber qué datos tienen");
    }

    private long retentionSafeOverdueCount() {
        return requests.findAll().stream().filter(r -> r.isOverdue(time.today())).count();
    }

    private void publishAllNotices() {
        for (LegalTextKind kind : LegalTextKind.values()) {
            if (kind.isNotice()) {
                legalTexts.publishFromTemplate(kind, admin.getId());
            }
        }
    }

    private ContactArea area() {
        return areas.findAll().stream().findFirst().orElseGet(() -> areas.save(new ContactArea("Admisión", "admision@colegio.cl", 1)));
    }

    private Course course() {
        GradeLevel level = levels.findAll().stream().findFirst()
                .orElseGet(() -> levels.save(new GradeLevel("1° básico", EducationStage.PRIMARY, null, 1)));
        return courses.findAll().stream().findFirst().orElseGet(() -> courses.save(new Course(level, "A", LocalDate.now().getYear())));
    }

    private Event openHouse() {
        LocalDateTime start = time.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Event event = new Event("puertas-abiertas", "Puertas abiertas", EventKind.OPEN_HOUSE, start, start.plusHours(2));
        event.openRegistration(50, false, null);
        event.publish();
        return calendar.save(event);
    }

    private void backdate(String table, long id, Instant at) {
        jdbc.update("update " + table + " set created_at = ?, updated_at = ? where id = ?", Timestamp.from(at), Timestamp.from(at), id);
    }
}
