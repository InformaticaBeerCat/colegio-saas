package cl.colegiosaas.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsCategoryRepository extends JpaRepository<NewsCategory, Long> {

    Optional<NewsCategory> findBySlug(String slug);

    List<NewsCategory> findAllByOrderByNameAsc();
}
