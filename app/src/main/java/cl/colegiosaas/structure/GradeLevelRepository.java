package cl.colegiosaas.structure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, Long> {

    List<GradeLevel> findAllByOrderBySortOrderAsc();
}
