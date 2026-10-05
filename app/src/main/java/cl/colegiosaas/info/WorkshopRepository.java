package cl.colegiosaas.info;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WorkshopRepository extends JpaRepository<Workshop, Long> {

    @EntityGraph(attributePaths = "gradeLevels")
    List<Workshop> findByAcademicYearAndActiveTrueOrderByNameAsc(int academicYear);

    @Query("select distinct w from Workshop w left join fetch w.gradeLevels")
    List<Workshop> findAllWithLevels();

    @EntityGraph(attributePaths = "gradeLevels")
    Optional<Workshop> findWithLevelsById(Long id);
}
