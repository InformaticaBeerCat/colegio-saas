package cl.colegiosaas.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    @EntityGraph(attributePaths = {"category", "gradeLevels", "featuredImage", "featuredImage.file", "featuredImage.blurredFile"})
    Optional<NewsArticle> findBySlug(String slug);

    /** Para el editor: autor, revisor, categoría y niveles en una sola consulta (no hay sesión abierta en la vista). */
    @EntityGraph(attributePaths = {"author", "reviewer", "category", "gradeLevels", "featuredImage"})
    Optional<NewsArticle> findWithDetailsById(Long id);

    /** Misma regla que {@link NewsArticle#isVisibleAt}, resuelta en la base. */
    @Query("""
            select n from NewsArticle n
            where n.status = cl.colegiosaas.news.NewsStatus.PUBLISHED
               or (n.status = cl.colegiosaas.news.NewsStatus.SCHEDULED and n.publishAt <= :now)
            order by n.publishAt desc
            """)
    Page<NewsArticle> findVisibleAt(Instant now, Pageable page);

    /**
     * Listado público con filtros opcionales por categoría y nivel (NOT-01). Una noticia sin niveles es
     * para todo el colegio: aparece en cualquier filtro de nivel.
     */
    @EntityGraph(attributePaths = {"category", "featuredImage", "featuredImage.file", "featuredImage.blurredFile"})
    @Query(value = """
            select n from NewsArticle n
            where (n.status = cl.colegiosaas.news.NewsStatus.PUBLISHED
                   or (n.status = cl.colegiosaas.news.NewsStatus.SCHEDULED and n.publishAt <= :now))
              and (:categoryId is null or n.category.id = :categoryId)
              and (:gradeLevelId is null or n.gradeLevels is empty
                   or exists (select 1 from n.gradeLevels l where l.id = :gradeLevelId))
            order by n.publishAt desc
            """)
    Page<NewsArticle> findVisible(Instant now, Long categoryId, Long gradeLevelId, Pageable page);

    boolean existsBySlug(String slug);

    /** Para la tarea que publica las programadas. */
    List<NewsArticle> findByStatusAndPublishAtLessThanEqual(NewsStatus status, Instant now);

    List<NewsArticle> findByStatusOrderByUpdatedAtAsc(NewsStatus status);
}
