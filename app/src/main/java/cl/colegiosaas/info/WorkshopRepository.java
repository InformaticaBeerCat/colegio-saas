package cl.colegiosaas.info;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkshopRepository extends JpaRepository<Workshop, Long> {

    List<Workshop> findByAcademicYearAndActiveTrueOrderByNameAsc(int academicYear);
}
