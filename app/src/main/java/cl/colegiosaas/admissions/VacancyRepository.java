package cl.colegiosaas.admissions;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    @EntityGraph(attributePaths = "gradeLevel")
    List<Vacancy> findByAcademicYearOrderByGradeLevel_SortOrderAsc(int academicYear);
}
