package cl.colegiosaas.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    Optional<NewsArticle> findBySlug(String slug);

    /** Misma regla que {@link NewsArticle#isVisibleAt}, resuelta en la base. */
    @Query("""
            select n from NewsArticle n
            where n.status = cl.colegiosaas.news.NewsStatus.PUBLISHED
               or (n.status = cl.colegiosaas.news.NewsStatus.SCHEDULED and n.publishAt <= :now)
            order by n.publishAt desc
            """)
    Page<NewsArticle> findVisibleAt(Instant now, Pageable page);

    /** Para la tarea que publica las programadas. */
    List<NewsArticle> findByStatusAndPublishAtLessThanEqual(NewsStatus status, Instant now);

    List<NewsArticle> findByStatusOrderByUpdatedAtAsc(NewsStatus status);
}
