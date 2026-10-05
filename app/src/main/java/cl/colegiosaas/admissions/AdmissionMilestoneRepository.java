package cl.colegiosaas.admissions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdmissionMilestoneRepository extends JpaRepository<AdmissionMilestone, Long> {

    List<AdmissionMilestone> findByProcessYearOrderBySortOrderAsc(int processYear);
}
