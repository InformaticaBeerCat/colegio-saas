package cl.colegiosaas.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {

    Optional<RetentionPolicy> findByDataCategory(RetentionCategory dataCategory);
}
