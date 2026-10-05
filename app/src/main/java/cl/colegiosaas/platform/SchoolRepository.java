package cl.colegiosaas.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {

    Optional<School> findByCustomDomain(String customDomain);

    Optional<School> findBySubdomain(String subdomain);

    boolean existsBySubdomain(String subdomain);
}
