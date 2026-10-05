package cl.colegiosaas.security;

import cl.colegiosaas.identity.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

/** Spring Security lo usa para cargar a quien intenta ingresar con email y contraseña. */
@Service
class SchoolUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;
    private final Clock clock;

    SchoolUserDetailsService(UserAccountRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(account -> SchoolUser.of(account, clock.instant()))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
