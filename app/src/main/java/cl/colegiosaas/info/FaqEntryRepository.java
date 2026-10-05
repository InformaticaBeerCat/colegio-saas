package cl.colegiosaas.info;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FaqEntryRepository extends JpaRepository<FaqEntry, Long> {

    @EntityGraph(attributePaths = "category")
    List<FaqEntry> findByPublishedTrueOrderByCategory_SortOrderAscSortOrderAsc();

    List<FaqEntry> findByCategoryAndPublishedTrueOrderBySortOrderAsc(FaqCategory category);

    @Query("select e from FaqEntry e join fetch e.category c order by c.sortOrder, e.sortOrder")
    List<FaqEntry> findAllWithCategory();

    @EntityGraph(attributePaths = "category")
    Optional<FaqEntry> findWithCategoryById(Long id);
}
