package cl.colegiosaas.scheduling;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppointmentTypeRepository extends JpaRepository<AppointmentType, Long> {

    List<AppointmentType> findByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = "hosts")
    List<AppointmentType> findWithHostsByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = "hosts")
    List<AppointmentType> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "hosts")
    Optional<AppointmentType> findWithHostsById(Long id);
}
