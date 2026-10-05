package cl.colegiosaas.page;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    /** Entradas de un menú con su página cargada, en orden estable; el árbol se arma en memoria. */
    @EntityGraph(attributePaths = {"page", "parent"})
    List<MenuItem> findByMenuOrderBySortOrderAscIdAsc(MenuLocation menu);

    boolean existsByPage(Page page);
}
