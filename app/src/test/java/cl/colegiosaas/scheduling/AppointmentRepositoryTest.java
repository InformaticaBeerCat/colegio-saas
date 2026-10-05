package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.shared.security.SecureTokens;
import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class AppointmentRepositoryTest {

    @Autowired
    AppointmentRepository appointments;

    @Autowired
    AppointmentTypeRepository types;

    @Autowired
    AvailabilityBlockRepository blocks;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    UserAccount admissionsOfficer;
    AppointmentType guidedVisit;
    final LocalDateTime tuesday10 = LocalDateTime.of(2026, 10, 13, 10, 0);

    @BeforeEach
    void setUp() {
        admissionsOfficer = fixtures.user("admision@colegio.cl", Role.SCHEDULE_MANAGER);
        guidedVisit = new AppointmentType("Visita guiada", 45, AppointmentAudience.PROSPECTIVE_FAMILY);
        guidedVisit.addHost(admissionsOfficer);
        types.save(guidedVisit);
    }

    @Test
    void bookingComputesTheEndAndTheLinkWorksOnlyWithItsToken() {
        Appointment.Booking booking = book(tuesday10, MeetingMode.IN_PERSON);
        em.flush();
        em.clear();

        Appointment loaded = appointments.findByManageTokenHash(SecureTokens.hash(booking.manageToken())).orElseThrow();
        assertThat(loaded.getEndsAt()).isEqualTo(tuesday10.plusMinutes(45));
        assertThat(loaded.getContactEmail()).isEqualTo("familia@mail.cl");
        assertThat(loaded.matchesToken(booking.manageToken())).isTrue();
        assertThat(loaded.matchesToken("token-adivinado")).isFalse();
    }

    @Test
    void cancelledAppointmentsFreeTheSlot() {
        Appointment first = book(tuesday10, MeetingMode.IN_PERSON).appointment();
        book(tuesday10.plusHours(1), MeetingMode.IN_PERSON);
        first.cancel("No podemos asistir", Instant.now());
        em.flush();

        assertThat(appointments.findActiveForHost(admissionsOfficer, tuesday10, tuesday10.plusHours(3)))
                .extracting(Appointment::getStartsAt)
                .containsExactly(tuesday10.plusHours(1));
        assertThatThrownBy(first::markAttended).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bookingRespectsModeAndHost() {
        assertThatThrownBy(() -> book(tuesday10, MeetingMode.ONLINE)).isInstanceOf(IllegalArgumentException.class);

        UserAccount teacher = fixtures.user("profesor@colegio.cl", Role.SCHEDULE_MANAGER);
        assertThatThrownBy(() -> Appointment.book(guidedVisit, teacher, tuesday10, MeetingMode.IN_PERSON,
                new DataSubject("Familia", "familia@mail.cl"), null,
                fixtures.consent("familia@mail.cl", ConsentPurpose.SCHEDULING), fixtures.index()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schoolWideBlocksAffectEveryHost() {
        blocks.save(new AvailabilityBlock(null, tuesday10.withHour(8), tuesday10.withHour(13), "Jornada de reflexión"));
        blocks.save(new AvailabilityBlock(fixtures.user("otro@colegio.cl"), tuesday10, tuesday10.plusHours(1), "Licencia"));
        em.flush();

        assertThat(blocks.findAffecting(admissionsOfficer, tuesday10, tuesday10.plusMinutes(45)))
                .extracting(AvailabilityBlock::getReason)
                .containsExactly("Jornada de reflexión");
    }

    @Test
    void availabilityRuleAppliesOnItsWeekdayWithinItsDates() {
        AvailabilityRule tuesdays = new AvailabilityRule(admissionsOfficer, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 30));
        tuesdays.setValidUntil(LocalDate.of(2026, 12, 15));

        assertThat(tuesdays.appliesOn(LocalDate.of(2026, 10, 13))).isTrue();
        assertThat(tuesdays.appliesOn(LocalDate.of(2026, 10, 14))).isFalse();
        assertThat(tuesdays.appliesOn(LocalDate.of(2026, 12, 22))).isFalse();
        assertThatThrownBy(() -> new AvailabilityRule(admissionsOfficer, DayOfWeek.MONDAY, LocalTime.NOON, LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Appointment.Booking book(LocalDateTime start, MeetingMode mode) {
        Appointment.Booking booking = Appointment.book(guidedVisit, admissionsOfficer, start, mode,
                new DataSubject("Familia González", "familia@mail.cl"), "+56922222222",
                fixtures.consent("familia@mail.cl", ConsentPurpose.SCHEDULING), fixtures.index());
        appointments.save(booking.appointment());
        return booking;
    }
}
