package cl.colegiosaas.calendar;

import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class EventRepositoryTest {

    @Autowired
    EventRepository events;

    @Autowired
    EventRegistrationRepository registrations;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    final LocalDateTime openHouseStart = LocalDateTime.of(2026, 10, 24, 10, 0);

    @Test
    void calendarReturnsPublishedEventsThatOverlapTheMonth() {
        Event winterBreak = Event.allDay("vacaciones", "Vacaciones de invierno", EventKind.VACATION,
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 10));
        winterBreak.publish();
        Event draft = new Event("borrador", "Borrador", EventKind.OTHER, LocalDateTime.of(2026, 7, 2, 9, 0), LocalDateTime.of(2026, 7, 2, 10, 0));
        events.save(winterBreak);
        events.save(draft);
        em.flush();

        assertThat(events.findPublishedBetween(LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0)))
                .extracting(Event::getSlug)
                .containsExactly("vacaciones");
    }

    @Test
    void eventCannotEndBeforeItStarts() {
        assertThatThrownBy(() -> new Event("x", "X", EventKind.OTHER, openHouseStart, openHouseStart.minusHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrationsCountAgainstCapacityAndWaitlistKeepsOrder() {
        Event openHouse = openHouse(3);
        EventRegistration.Created family = register(openHouse, "familia1@mail.cl", 2, null);
        EventRegistration.Created waiting1 = register(openHouse, "familia2@mail.cl", 2, 1);
        register(openHouse, "familia3@mail.cl", 1, 2);
        em.flush();

        assertThat(registrations.countConfirmedAttendees(openHouse)).isEqualTo(2);
        assertThat(openHouse.seatsLeft(2)).hasValue(1);

        family.registration().cancel(Instant.now());
        EventRegistration next = registrations
                .findFirstByEventAndStatusOrderByWaitlistPositionAsc(openHouse, RegistrationStatus.WAITLISTED)
                .orElseThrow();
        assertThat(next).isEqualTo(waiting1.registration());
        next.promoteFromWaitlist();
        em.flush();

        assertThat(registrations.countConfirmedAttendees(openHouse)).isEqualTo(2);
    }

    @Test
    void personalDataIsEncryptedAndTheLinkTokenOnlyStoredAsHash() {
        Event openHouse = openHouse(null);
        EventRegistration.Created created = register(openHouse, "ana@mail.cl", 1, null);
        em.flush();

        String storedEmail = jdbc.queryForObject("select email from event_registration", String.class);
        assertThat(storedEmail).startsWith("v1:").doesNotContain("ana@mail.cl");
        assertThat(created.registration().matchesToken(created.manageToken())).isTrue();
        assertThat(created.registration().getManageTokenHash()).isNotEqualTo(created.manageToken());

        em.clear();
        assertThat(registrations.findAll()).extracting(EventRegistration::getEmail).containsExactly("ana@mail.cl");
    }

    @Test
    void registrationClosesAutomatically() {
        Event openHouse = openHouse(10);

        assertThat(openHouse.acceptsRegistrationsAt(openHouseStart.minusDays(1))).isTrue();
        assertThat(openHouse.acceptsRegistrationsAt(openHouseStart.plusMinutes(1))).isFalse();
        openHouse.closeRegistration();
        assertThat(openHouse.acceptsRegistrationsAt(openHouseStart.minusDays(1))).isFalse();
    }

    private Event openHouse(Integer capacity) {
        Event event = new Event("puertas-abiertas", "Puertas abiertas", EventKind.OPEN_HOUSE, openHouseStart, openHouseStart.plusHours(2));
        event.openRegistration(capacity, true, null);
        event.publish();
        return events.save(event);
    }

    private EventRegistration.Created register(Event event, String email, int attendees, Integer waitlistPosition) {
        var registrant = new EventRegistration.Registrant(new DataSubject("Apoderado", email), "+56911111111", attendees, null, null);
        var consent = fixtures.consent(email, ConsentPurpose.EVENT_REGISTRATION);
        EventRegistration.Created created = waitlistPosition == null
                ? EventRegistration.confirmed(event, registrant, consent, fixtures.index())
                : EventRegistration.waitlisted(event, registrant, waitlistPosition, consent, fixtures.index());
        registrations.save(created.registration());
        return created;
    }
}
