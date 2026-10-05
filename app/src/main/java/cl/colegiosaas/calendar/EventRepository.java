package cl.colegiosaas.calendar;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = {"gradeLevels", "courses", "courses.gradeLevel"})
    Optional<Event> findBySlug(String slug);

    @EntityGraph(attributePaths = {"gradeLevels", "courses", "courses.gradeLevel"})
    Optional<Event> findWithTargetsById(Long id);

    boolean existsBySlug(String slug);

    /** Todos los eventos del rango, publicados o no (panel). */
    @Query("""
            select e from Event e
            where e.startsAt < :to and e.endsAt >= :from
            order by e.startsAt
            """)
    List<Event> findBetween(LocalDateTime from, LocalDateTime to);

    /** Eventos publicados que se cruzan con el rango [from, to): sirve para la vista mensual y el .ics. */
    @EntityGraph(attributePaths = "gradeLevels")
    @Query("""
            select distinct e from Event e
            where e.publishedAt is not null
              and e.startsAt < :to and e.endsAt >= :from
            order by e.startsAt
            """)
    List<Event> findPublishedBetween(LocalDateTime from, LocalDateTime to);
}
