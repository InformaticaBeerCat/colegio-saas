package cl.colegiosaas.privacy.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Lo que las páginas públicas necesitan para el banner de cookies: la elección del visitante y la ruta
 * actual, para volver a ella después de elegir. Las plantillas lo leen como {@code cookies} y {@code currentPath}.
 */
@ControllerAdvice(basePackages = {"cl.colegiosaas.publicsite", "cl.colegiosaas.privacy.web"})
class PublicPrivacyAdvice {

    private final CookiePreferences preferences;

    PublicPrivacyAdvice(CookiePreferences preferences) {
        this.preferences = preferences;
    }

    @ModelAttribute("cookies")
    CookiePreferences.Choice cookies(HttpServletRequest request) {
        return preferences.read(request);
    }

    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null ? "" : "?" + query);
    }
}
