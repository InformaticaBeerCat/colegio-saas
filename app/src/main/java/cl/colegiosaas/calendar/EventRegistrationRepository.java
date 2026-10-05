package cl.colegiosaas.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    Optional<EventRegistration> findByManageTokenHash(String manageTokenHash);

    /** Personas confirmadas: lo que se resta del aforo. */
    @Query("""
            select coalesce(sum(r.attendees), 0) from EventRegistration r
            where r.event = :event and r.status = cl.colegiosaas.calendar.RegistrationStatus.CONFIRMED
            """)
    int countConfirmedAttendees(Event event);

    /** Primero de la lista de espera, para promoverlo cuando alguien cancela. */
    Optional<EventRegistration> findFirstByEventAndStatusOrderByWaitlistPositionAsc(Event event, RegistrationStatus status);

    List<EventRegistration> findByEventOrderByCreatedAtAsc(Event event);
}
