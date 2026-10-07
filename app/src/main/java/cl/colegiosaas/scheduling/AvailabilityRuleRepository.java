package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, Long> {

    List<AvailabilityRule> findByHost(UserAccount host);

    @EntityGraph(attributePaths = "appointmentType")
    List<AvailabilityRule> findWithTypeByHostOrderByDayOfWeekAscStartTimeAsc(UserAccount host);
}
