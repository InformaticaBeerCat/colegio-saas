package cl.colegiosaas.identity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);

    /** Para invalidar los enlaces anteriores al emitir uno nuevo. */
    List<AccountToken> findByUserAndPurposeAndUsedAtIsNull(UserAccount user, AccountTokenPurpose purpose);

    List<AccountToken> findByUserAndUsedAtIsNull(UserAccount user);
}
