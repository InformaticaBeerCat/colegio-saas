package cl.colegiosaas.analytics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/** Cuenta las páginas del sitio público que se mostraron bien; nada del panel ni de los enlaces personales. */
class PageViewInterceptor implements HandlerInterceptor {

    private static final List<String> SKIPPED = List.of("/admin", "/setup", "/citas/", "/inscripciones/", "/privacidad/derechos/estado",
            "/buscar");

    private final PageViewCounter counter;

    PageViewInterceptor(PageViewCounter counter) {
        this.counter = counter;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String path = request.getRequestURI();
        String contentType = response.getContentType();
        if (ex != null || !"GET".equals(request.getMethod()) || response.getStatus() != 200
                || contentType == null || !contentType.startsWith("text/html")
                || SKIPPED.stream().anyMatch(path::startsWith) || isPrefetch(request)) {
            return;
        }
        boolean optedOut = "1".equals(request.getHeader("DNT")) || "1".equals(request.getHeader("Sec-GPC"));
        counter.record(path, request.getHeader("User-Agent"), request.getHeader("Referer"), optedOut);
    }

    private static boolean isPrefetch(HttpServletRequest request) {
        String purpose = request.getHeader("Sec-Purpose");
        if (purpose == null) {
            purpose = request.getHeader("Purpose");
        }
        return purpose != null && purpose.contains("prefetch");
    }
}
