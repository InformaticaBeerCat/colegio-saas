package cl.colegiosaas.contact;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class InquiryRepositoryTest {

    @Autowired
    InquiryRepository inquiries;

    @Autowired
    ContactAreaRepository areas;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void inquiryGetsATicketAndItsTextIsEncrypted() {
        ContactArea admissions = areas.save(new ContactArea("Admisión", "admision@colegio.cl", 1));
        Inquiry inquiry = inquiries.saveAndFlush(newInquiry(admissions, "¿Quedan vacantes en Kínder 2027?"));
        em.clear();

        assertThat(inquiry.getTicketCode()).matches("C-[2-9A-Z]{8}");
        assertThat(jdbc.queryForObject("select message from inquiry", String.class)).startsWith("v1:").doesNotContain("Kínder");
        assertThat(inquiries.findByTicketCode(inquiry.getTicketCode())).get()
                .extracting(Inquiry::getMessage).isEqualTo("¿Quedan vacantes en Kínder 2027?");
    }

    @Test
    void inboxFiltersByAreaAndStatusAndTracksFirstResponse() {
        ContactArea admissions = areas.save(new ContactArea("Admisión", "admision@colegio.cl", 1));
        ContactArea finance = areas.save(new ContactArea("Finanzas", "finanzas@colegio.cl", 2));
        UserAccount staff = fixtures.user("secretaria@colegio.cl", Role.EDITOR);
        Inquiry open = inquiries.save(newInquiry(admissions, "Consulta 1"));
        Inquiry done = inquiries.save(newInquiry(admissions, "Consulta 2"));
        inquiries.save(newInquiry(finance, "Consulta 3"));
        em.flush();

        Instant responded = open.getCreatedAt().plusSeconds(3600);
        open.recordResponse(responded);
        open.addNote(staff, "Llamé por teléfono, envía certificado mañana");
        done.resolve(Instant.now());
        em.flush();
        em.clear();

        var inbox = inquiries.findByAreaAndStatusInOrderByCreatedAtAsc(
                admissions, Set.of(InquiryStatus.NEW, InquiryStatus.IN_PROGRESS), PageRequest.of(0, 20));
        assertThat(inbox).singleElement().satisfies(inquiry -> {
            assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
            assertThat(inquiry.timeToFirstResponse()).hasValueSatisfying(d -> assertThat(d.toMinutes()).isEqualTo(60));
            assertThat(inquiry.getNotes()).singleElement()
                    .extracting(InquiryNote::getBody).asString().contains("certificado");
        });
    }

    private Inquiry newInquiry(ContactArea area, String message) {
        return new Inquiry(area, new DataSubject("Ana Pérez", "ana@mail.cl"), "+56911111111", "Consulta", message,
                fixtures.consent("ana@mail.cl", ConsentPurpose.CONTACT), fixtures.index());
    }
}
