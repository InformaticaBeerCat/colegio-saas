package cl.colegiosaas.web;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditLogRepository;
import cl.colegiosaas.contact.ContactArea;
import cl.colegiosaas.contact.ContactAreaRepository;
import cl.colegiosaas.contact.Inquiry;
import cl.colegiosaas.contact.InquiryRepository;
import cl.colegiosaas.contact.InquiryStatus;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.site.FooterService;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contacto con enrutamiento por área, ticket y bandeja (COM-01, COM-02), antispam (COM-08) y WhatsApp (COM-03). */
class ContactTest extends WebTestSupport {

    @Autowired LegalTextService legalTexts;
    @Autowired ContactAreaRepository areas;
    @Autowired InquiryRepository inquiries;
    @Autowired AuditLogRepository auditLog;
    @Autowired SchoolRepository schools;

    UserAccount admin;
    UserAccount editor;
    ContactArea secretaria;
    ContactArea admision;

    @BeforeEach
    void setUp() {
        install();
        admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
        secretaria = areas.findAll().getFirst();
        admision = areas.save(new ContactArea("Admisión", "admision@colegio.cl", 2));
    }

    @Test
    void theInstallationCreatesAnAreaAndTheFormWaitsForItsNotice() throws Exception {
        assertThat(secretaria.getName()).isEqualTo("Secretaría");
        assertThat(secretaria.getNotifyEmail()).isEqualTo("contacto@colegio.cl");

        mvc.perform(get("/contacto"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El formulario se habilitará pronto")));
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_CONTACT, admin.getId());
        mvc.perform(get("/contacto"))
                .andExpect(content().string(containsString("Admisión")))
                .andExpect(content().string(containsString("name=\"sello\"")))
                .andExpect(content().string(containsString("name=\"sitio_web\"")))
                .andExpect(content().string(containsString("Aviso de privacidad: formulario de contacto (versión 1)")));
    }

    @Test
    void aMessageGetsATicketGoesToItsAreaAndIsAnsweredFromTheInbox() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_CONTACT, admin.getId());

        mvc.perform(message().param("consent", "false"))
                .andExpect(content().string(containsString("marca la casilla")))
                .andExpect(content().string(containsString("¿Quedan vacantes en kínder?")));
        assertThat(inquiries.count()).isZero();

        mvc.perform(message().param("consent", "true"))
                .andExpect(redirectedUrl("/contacto/enviado"))
                .andExpect(flash().attributeExists("ticket"));
        Inquiry inquiry = inquiries.findAll().getFirst();
        assertThat(inquiry.getTicketCode()).startsWith("C-");
        assertThat(inquiry.getArea()).isEqualTo(admision);
        assertThat(inquiry.getConsent().getPurpose()).isEqualTo(ConsentPurpose.CONTACT);
        assertThat(inquiry.getConsent().getLegalText().getKind()).isEqualTo(LegalTextKind.NOTICE_CONTACT);

        assertThat(mails()).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("rosa@correo.cl");
            assertThat(m.body()).contains(inquiry.getTicketCode(), "Admisión");
        });
        // Al área le llega el aviso con el enlace, sin los datos de la persona.
        OutgoingMail toArea = mails().stream().filter(m -> m.to().equals("admision@colegio.cl")).findFirst().orElseThrow();
        assertThat(toArea.body()).contains("/admin/inquiries/" + inquiry.getId()).doesNotContain("rosa@correo.cl", "kínder");

        mvc.perform(get("/admin/inquiries").with(as(editor)))
                .andExpect(content().string(containsString(inquiry.getTicketCode())))
                .andExpect(content().string(containsString("Nueva")));
        mvc.perform(get("/admin/inquiries").param("area", String.valueOf(secretaria.getId())).with(as(editor)))
                .andExpect(content().string(not(containsString(inquiry.getTicketCode()))));
        mvc.perform(get("/admin/inquiries/" + inquiry.getId()).with(as(editor)))
                .andExpect(content().string(containsString("rosa@correo.cl")))
                .andExpect(content().string(containsString("¿Quedan vacantes en kínder?")));
        assertThat(auditLog.findByActionOrderByIdAsc(AuditAction.VIEW_PERSONAL_DATA))
                .anySatisfy(e -> assertThat(e.getEntityType()).isEqualTo("Inquiry"));

        mvc.perform(post("/admin/inquiries/" + inquiry.getId() + "/note").param("text", "Llamar a la familia")
                .with(as(editor)).with(csrf()));
        mvc.perform(post("/admin/inquiries/" + inquiry.getId() + "/reply").param("text", "Sí, quedan 3 vacantes.")
                        .with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("notice", "Respuesta enviada por correo"));
        assertThat(inquiry.getFirstResponseAt()).isNotNull();
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
        assertThat(mails()).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("rosa@correo.cl");
            assertThat(m.body()).contains("Sí, quedan 3 vacantes.", "comunicaciones");
        });
        mvc.perform(get("/admin/inquiries/" + inquiry.getId()).with(as(editor)))
                .andExpect(content().string(containsString("Llamar a la familia")))
                .andExpect(content().string(containsString("Respuesta enviada:")));

        mvc.perform(post("/admin/inquiries/" + inquiry.getId() + "/route").param("areaId", String.valueOf(secretaria.getId()))
                .with(as(editor)).with(csrf()));
        assertThat(inquiry.getArea()).isEqualTo(secretaria);
        assertThat(mails()).anySatisfy(m -> assertThat(m.subject()).startsWith("Consulta derivada"));

        mvc.perform(post("/admin/inquiries/" + inquiry.getId() + "/resolve").with(as(editor)).with(csrf()));
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.RESOLVED);
        mvc.perform(get("/admin/inquiries").with(as(editor)))
                .andExpect(content().string(not(containsString(inquiry.getTicketCode()))))
                .andExpect(content().string(containsString("Primera respuesta en los últimos 30 días")));
        mvc.perform(get("/admin/inquiries").param("folder", "RESOLVED").with(as(editor)))
                .andExpect(content().string(containsString(inquiry.getTicketCode())));
    }

    @Test
    void botsAreAnsweredAsUsualButNothingIsStored() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_CONTACT, admin.getId());
        mvc.perform(message().param("consent", "true").param("sitio_web", "https://spam.example"))
                .andExpect(redirectedUrl("/contacto/enviado"));
        mvc.perform(post("/contacto").param("areaId", String.valueOf(admision.getId())).param("name", "Bot")
                        .param("email", "bot@spam.example").param("message", "Compre ya").param("consent", "true").with(csrf()))
                .andExpect(redirectedUrl("/contacto/enviado"));
        assertThat(inquiries.count()).isZero();
        assertThat(mails()).isEmpty();
    }

    @Test
    void inactiveAreasDoNotReceiveMessages() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_CONTACT, admin.getId());
        admision.setActive(false);
        mvc.perform(message().param("consent", "true"))
                .andExpect(content().string(containsString("Elige a quién va dirigido")));
    }

    @Test
    void areasAreManagedBySchoolAdminsAndOneMustStayActive() throws Exception {
        mvc.perform(get("/admin/contact-areas").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/contact-areas").param("name", "Convivencia escolar").param("notifyEmail", "convivencia@colegio.cl")
                        .param("active", "true").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Área creada"));
        mvc.perform(post("/admin/contact-areas").param("name", "admisión").param("notifyEmail", "x@colegio.cl")
                        .param("active", "true").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", "Ya existe un área con ese nombre"));
        mvc.perform(post("/admin/contact-areas").param("name", "Finanzas").param("notifyEmail", "no-es-correo")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("correo")));

        for (ContactArea area : areas.findAll()) {
            if (!area.getName().equals("Secretaría")) {
                area.setActive(false);
            }
        }
        mvc.perform(post("/admin/contact-areas").param("id", String.valueOf(secretaria.getId())).param("name", "Secretaría")
                        .param("notifyEmail", "contacto@colegio.cl").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("al menos un área activa")));
    }

    @Test
    void whatsappAppearsOnTheContactPageWhenConfigured() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_CONTACT, admin.getId());
        mvc.perform(get("/contacto")).andExpect(content().string(not(containsString("wa.me"))));
        var current = footer.current();
        footer.update(new cl.colegiosaas.site.FooterSettings(current.footerText(), "+56912345678", current.socialLinks(),
                current.phone(), current.contactEmail(), current.address()));
        mvc.perform(get("/contacto")).andExpect(content().string(containsString("https://wa.me/56912345678")));
    }

    @Autowired FooterService footer;

    private MockHttpServletRequestBuilder message() {
        return publicForm("/contacto").param("areaId", String.valueOf(admision.getId())).param("name", "Rosa Muñoz")
                .param("email", "rosa@correo.cl").param("subject", "Admisión 2027").param("message", "¿Quedan vacantes en kínder?");
    }
}
