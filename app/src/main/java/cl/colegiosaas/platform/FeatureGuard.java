package cl.colegiosaas.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** Aplica {@link RequiresFeature} antes de ejecutar el controlador. */
class FeatureGuard implements HandlerInterceptor {

    private final SchoolRepository schools;

    FeatureGuard(SchoolRepository schools) {
        this.schools = schools;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresFeature required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresFeature.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresFeature.class);
        }
        if (required == null) {
            return true;
        }
        Feature feature = required.value();
        boolean enabled = schools.findSingleton().map(school -> school.hasFeature(feature)).orElse(false);
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return true;
    }
}
