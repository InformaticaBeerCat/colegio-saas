package cl.colegiosaas.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByHolidayDateBetween(LocalDate from, LocalDate to);

    boolean existsByHolidayDate(LocalDate date);

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);
}
