package cl.colegiosaas.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface SiteAlertRepository extends JpaRepository<SiteAlert, Long> {

    /** Misma regla que {@link SiteAlert#isVisibleAt}, resuelta en la base de datos. */
    @Query("""
            select a from SiteAlert a
            where a.active = true
              and (a.startsAt is null or a.startsAt <= :now)
              and (a.endsAt is null or a.endsAt > :now)
            order by a.createdAt desc
            """)
    List<SiteAlert> findVisibleAt(Instant now);
}
