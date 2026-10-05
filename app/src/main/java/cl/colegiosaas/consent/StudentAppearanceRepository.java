package cl.colegiosaas.consent;

import cl.colegiosaas.media.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudentAppearanceRepository extends JpaRepository<StudentAppearance, Long> {

    /** Todas las fotos donde aparece un estudiante: lo que hay que retirar si revoca (MED-09). */
    @Query("""
            select m from MediaAsset m left join fetch m.file left join fetch m.blurredFile
            where m in (select a.asset from StudentAppearance a where a.student = :student)
            """)
    List<MediaAsset> findAssetsShowing(Student student);

    @Query("select a.student from StudentAppearance a join fetch a.student.course c join fetch c.gradeLevel where a.asset = :asset")
    List<Student> findStudentsIn(MediaAsset asset);

    Optional<StudentAppearance> findByStudentAndAsset(Student student, MediaAsset asset);
}
