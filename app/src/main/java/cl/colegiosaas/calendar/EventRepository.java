package cl.colegiosaas.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findBySlug(String slug);

    /** Eventos publicados que se cruzan con el rango [from, to): sirve para la vista mensual y el .ics. */
    @Query("""
            select e from Event e
            where e.publishedAt is not null
              and e.startsAt < :to and e.endsAt >= :from
            order by e.startsAt
            """)
    List<Event> findPublishedBetween(LocalDateTime from, LocalDateTime to);
}
