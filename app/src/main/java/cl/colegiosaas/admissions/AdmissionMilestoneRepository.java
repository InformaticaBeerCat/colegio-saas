package cl.colegiosaas.admissions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdmissionMilestoneRepository extends JpaRepository<AdmissionMilestone, Long> {

    List<AdmissionMilestone> findByProcessYearOrderBySortOrderAsc(int processYear);

    /** Hitos desde una fecha, para el sitio: los que ya pasaron no se muestran como próximos. */
    List<AdmissionMilestone> findByProcessYearOrderByStartsOnAscSortOrderAsc(int processYear);
}
