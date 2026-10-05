package cl.colegiosaas.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Qué pasa después de una contraseña correcta:
 * <ol>
 *   <li>Si la persona no necesita MFA, entra directo.</li>
 *   <li>Si lo necesita, la sesión queda como {@link MfaPendingAuthentication} y se la lleva a
 *       enrolarse (primera vez) o a ingresar su código.</li>
 * </ol>
 * {@link #complete} es el paso final común: deja la sesión con todos sus permisos.
 */
@Component
public class LoginFlow implements AuthenticationSuccessHandler {

    public static final String MFA_SETUP_URL = "/admin/mfa/setup";
    public static final String MFA_VERIFY_URL = "/admin/mfa/verify";

    private final SecurityContextRepository contextRepository;
    private final LoginAttempts attempts;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SavedRequestAwareAuthenticationSuccessHandler afterLogin = new SavedRequestAwareAuthenticationSuccessHandler();

    LoginFlow(SecurityContextRepository contextRepository, LoginAttempts attempts) {
        this.contextRepository = contextRepository;
        this.attempts = attempts;
        afterLogin.setDefaultTargetUrl("/admin");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        SchoolUser user = (SchoolUser) authentication.getPrincipal();
        if (!user.needsSecondFactor()) {
            complete(authentication, request, response, null);
            return;
        }
        store(new MfaPendingAuthentication(authentication), request, response);
        response.sendRedirect(request.getContextPath() + (user.isMfaEnabled() ? MFA_VERIFY_URL : MFA_SETUP_URL));
    }

    /**
     * Deja la sesión con el ingreso completo. Si viene desde el segundo factor, cambia el id de sesión
     * (como al ingresar la contraseña) para que nadie aproveche un id visto a medio camino.
     *
     * @param target adónde ir; nulo = la página que se pidió antes del login, o el panel
     */
    public void complete(Authentication full, HttpServletRequest request, HttpServletResponse response,
                         String target) throws IOException, ServletException {
        if (holder.getContext().getAuthentication() instanceof MfaPendingAuthentication) {
            request.changeSessionId();
        }
        store(full, request, response);
        attempts.recordSuccess(((SchoolUser) full.getPrincipal()).id());
        if (target != null) {
            response.sendRedirect(request.getContextPath() + target);
        } else {
            afterLogin.onAuthenticationSuccess(request, response, full);
        }
    }

    private void store(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
