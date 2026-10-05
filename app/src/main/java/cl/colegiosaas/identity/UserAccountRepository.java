package cl.colegiosaas.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Todas las consultas quedan filtradas al colegio de {@code TenantContext}. */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);
}
