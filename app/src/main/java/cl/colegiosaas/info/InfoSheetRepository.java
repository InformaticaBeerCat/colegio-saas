package cl.colegiosaas.info;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InfoSheetRepository extends JpaRepository<InfoSheet, Long> {

    List<InfoSheet> findByKindAndAcademicYearAndPublishedTrue(InfoSheetKind kind, int academicYear);
}
