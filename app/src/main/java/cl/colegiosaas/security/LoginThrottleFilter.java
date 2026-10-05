package cl.colegiosaas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

/** Corta el POST de login antes de verificar la contraseña si la IP superó el límite. */
class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginThrottle throttle;
    private final Clock clock;

    LoginThrottleFilter(LoginThrottle throttle, Clock clock) {
        this.throttle = throttle;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && (request.getContextPath() + "/admin/login").equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (throttle.isBlocked(request.getRemoteAddr(), clock.instant())) {
            response.sendRedirect(request.getContextPath() + "/admin/login?throttled");
            return;
        }
        chain.doFilter(request, response);
    }
}
