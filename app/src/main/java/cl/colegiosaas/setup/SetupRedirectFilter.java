package cl.colegiosaas.setup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Mientras no haya instalación, toda petición va a {@code /setup}. Corre antes que Spring Security:
 * sin colegio ni usuarios no tiene sentido mostrar el login.
 */
class SetupRedirectFilter extends OncePerRequestFilter {

    private static final List<String> ALWAYS_ALLOWED = List.of("/setup", "/css/", "/js/", "/images/",
            "/favicon.ico", "/error", "/actuator/health");

    private final InstallationService installation;

    SetupRedirectFilter(InstallationService installation) {
        this.installation = installation;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALWAYS_ALLOWED.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (installation.isInstalled()) {
            chain.doFilter(request, response);
        } else {
            response.sendRedirect(request.getContextPath() + "/setup");
        }
    }
}
