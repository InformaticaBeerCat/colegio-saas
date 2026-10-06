package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByManageTokenHash(String manageTokenHash);

    /** Agenda del gestor (AGE-10) y verificación de choques antes de reservar. */
    @Query("""
            select a from Appointment a
            where a.host = :host
              and a.status <> cl.colegiosaas.scheduling.AppointmentStatus.CANCELLED
              and a.startsAt < :to and a.endsAt > :from
            order by a.startsAt
            """)
    List<Appointment> findActiveForHost(UserAccount host, LocalDateTime from, LocalDateTime to);

    /** Citas confirmadas a las que aún no se les envía recordatorio (AGE-04). */
    List<Appointment> findByStatusAndReminderSentAtIsNullAndStartsAtBetween(
            AppointmentStatus status, LocalDateTime from, LocalDateTime to);

    List<Appointment> findByContactEmailHash(String contactEmailHash);

    /** Citas que terminaron antes del corte (hora del colegio), para la retención (PRV-06). */
    List<Appointment> findByEndsAtBefore(LocalDateTime cutoff);
}
