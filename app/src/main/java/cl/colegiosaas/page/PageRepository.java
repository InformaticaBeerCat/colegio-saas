package cl.colegiosaas.page;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {

    Optional<Page> findBySlug(String slug);

    Optional<Page> findFirstByKind(PageKind kind);

    boolean existsBySlug(String slug);
}
