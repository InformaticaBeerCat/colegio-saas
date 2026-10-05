package cl.colegiosaas.consent;

import cl.colegiosaas.structure.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Los nombres están cifrados: no se puede ordenar ni buscar por nombre en SQL, se hace en memoria. */
public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByCourseAndActiveTrue(Course course);

    List<Student> findByGuardianEmailHash(String guardianEmailHash);

    @EntityGraph(attributePaths = {"course", "course.gradeLevel"})
    List<Student> findByActiveTrue();

    @EntityGraph(attributePaths = {"course", "course.gradeLevel"})
    List<Student> findByCourse_AcademicYear(int academicYear);

    @EntityGraph(attributePaths = {"course", "course.gradeLevel"})
    Optional<Student> findWithCourseById(Long id);
}
