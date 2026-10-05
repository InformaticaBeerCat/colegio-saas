package cl.colegiosaas.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformOperatorRepository extends JpaRepository<PlatformOperator, Long> {

    Optional<PlatformOperator> findByEmail(String email);
}
