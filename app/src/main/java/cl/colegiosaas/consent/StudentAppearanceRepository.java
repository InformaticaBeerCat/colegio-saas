package cl.colegiosaas.consent;

import cl.colegiosaas.media.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StudentAppearanceRepository extends JpaRepository<StudentAppearance, Long> {

    /** Todas las fotos donde aparece un estudiante: lo que hay que retirar si revoca (MED-09). */
    @Query("select a.asset from StudentAppearance a where a.student = :student")
    List<MediaAsset> findAssetsShowing(Student student);

    @Query("select a.student from StudentAppearance a where a.asset = :asset")
    List<Student> findStudentsIn(MediaAsset asset);
}
