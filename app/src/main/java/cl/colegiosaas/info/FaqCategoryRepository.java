package cl.colegiosaas.info;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqCategoryRepository extends JpaRepository<FaqCategory, Long> {

    List<FaqCategory> findAllByOrderBySortOrderAsc();
}
