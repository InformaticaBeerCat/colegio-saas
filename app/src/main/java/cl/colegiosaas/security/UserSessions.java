package cl.colegiosaas.security;

import cl.colegiosaas.identity.UserAccessRevoked;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/**
 * Cierra las sesiones abiertas de una persona (USR-04): al darla de baja o cambiar sus roles, su
 * próxima petición la manda al login aunque tenga el panel abierto en otro computador.
 */
@Component
class UserSessions {

    private final SessionRegistry registry;

    UserSessions(SessionRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    void onAccessRevoked(UserAccessRevoked event) {
        for (Object principal : registry.getAllPrincipals()) {
            if (principal instanceof SchoolUser user && user.id() == event.userId()) {
                for (SessionInformation session : registry.getAllSessions(principal, false)) {
                    if (!session.getSessionId().equals(event.keepSessionId())) {
                        session.expireNow();
                    }
                }
            }
        }
    }
}
