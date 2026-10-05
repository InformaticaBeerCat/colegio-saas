package cl.colegiosaas.security;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/** Escucha los eventos que publica Spring Security al fallar un ingreso o al cerrar sesión. */
@Component
class AuthenticationEventsListener {

    private final LoginAttempts attempts;
    private final AuditTrail audit;

    AuthenticationEventsListener(LoginAttempts attempts, AuditTrail audit) {
        this.attempts = attempts;
        this.audit = audit;
    }

    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        String ip = event.getAuthentication().getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : null;
        attempts.recordFailure(event.getAuthentication().getName(), ip);
    }

    @EventListener
    void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof SchoolUser user) {
            audit.recordFor(user, AuditAction.LOGOUT, "UserAccount", user.id(), null);
        }
    }
}
