package cl.colegiosaas.info;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqEntryRepository extends JpaRepository<FaqEntry, Long> {

    @EntityGraph(attributePaths = "category")
    List<FaqEntry> findByPublishedTrueOrderByCategory_SortOrderAscSortOrderAsc();

    List<FaqEntry> findByCategoryAndPublishedTrueOrderBySortOrderAsc(FaqCategory category);
}
