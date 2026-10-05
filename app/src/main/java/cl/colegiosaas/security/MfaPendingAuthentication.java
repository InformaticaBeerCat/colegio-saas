package cl.colegiosaas.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.List;

/**
 * Sesión a medio camino: la contraseña fue correcta pero falta el código MFA. Su única autoridad es
 * {@link #AUTHORITY}, que solo abre las páginas del segundo factor; el ingreso real ({@link #primary()})
 * queda guardado y se restaura al verificar el código.
 */
public class MfaPendingAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String AUTHORITY = "MFA_PENDING";

    private final Authentication primary;

    public MfaPendingAuthentication(Authentication primary) {
        super(List.of(new SimpleGrantedAuthority(AUTHORITY)));
        this.primary = primary;
        setAuthenticated(true);
    }

    public Authentication primary() {
        return primary;
    }

    public SchoolUser user() {
        return (SchoolUser) primary.getPrincipal();
    }

    @Override
    public Object getPrincipal() {
        return primary.getPrincipal();
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
