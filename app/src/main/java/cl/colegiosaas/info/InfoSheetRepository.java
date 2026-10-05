package cl.colegiosaas.info;

import cl.colegiosaas.media.StoredFile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InfoSheetRepository extends JpaRepository<InfoSheet, Long> {

    List<InfoSheet> findByKindAndAcademicYearAndPublishedTrue(InfoSheetKind kind, int academicYear);

    @Query("select s from InfoSheet s left join fetch s.gradeLevel left join fetch s.file")
    List<InfoSheet> findAllWithDetails();

    @EntityGraph(attributePaths = {"gradeLevel", "file"})
    Optional<InfoSheet> findWithDetailsById(Long id);

    /** ¿El archivo es el PDF de alguna ficha publicada? */
    boolean existsByFileAndPublishedTrue(StoredFile file);
}
