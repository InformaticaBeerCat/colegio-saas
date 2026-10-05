package cl.colegiosaas.structure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /** Cursos del año ordenados como en una nómina: por nivel y luego por letra. */
    @EntityGraph(attributePaths = "gradeLevel")
    List<Course> findByAcademicYearOrderByGradeLevel_SortOrderAscSectionAsc(int academicYear);

    boolean existsByGradeLevel(GradeLevel gradeLevel);
}
