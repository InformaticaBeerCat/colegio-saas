package cl.colegiosaas.privacy.web;

import cl.colegiosaas.analytics.AnalyticsProperties;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Preferencia de cookies del visitante (PRV-03). El banner aparece solo si el colegio usa una herramienta de
 * analítica externa (REP-01): el conteo propio de visitas no usa cookies. Se guarda en el navegador: una cookie
 * necesaria con la versión de la política que vio y si aceptó la analítica. Si el colegio publica una
 * versión nueva de la política, el banner vuelve a aparecer.
 */
@Component
public class CookiePreferences {

    public static final String COOKIE = "preferencias_cookies";
    private static final Duration LIFETIME = Duration.ofDays(180);

    private final LegalTextService legalTexts;
    private final AnalyticsProperties analytics;

    CookiePreferences(LegalTextService legalTexts, AnalyticsProperties analytics) {
        this.legalTexts = legalTexts;
        this.analytics = analytics;
    }

    /**
     * Script de analítica externa para esta visita (REP-01, PRV-03): solo si el colegio configuró uno y la persona
     * lo aceptó en el banner sobre la política vigente. Nulo en cualquier otro caso.
     */
    public String analyticsScript() {
        if (!analytics.hasExternalScript()) {
            return null;
        }
        Choice choice = current();
        return choice != null && choice.analyticsAllowed() ? analytics.scriptUrl().strip() : null;
    }

    /**
     * @param policyVersion     versión vigente de la política de cookies; nula si no está publicada (no hay banner)
     * @param bannerNeeded      la persona no ha elegido sobre la versión vigente
     * @param analyticsAllowed  aceptó la analítica en la versión vigente: lo único que habilita su script (fase 8)
     */
    public record Choice(Integer policyVersion, boolean bannerNeeded, boolean analyticsAllowed) {
    }

    /**
     * Elección del visitante de la petición en curso. Las plantillas del sitio la piden con
     * {@code @cookiePreferences.current()} solo al pintar la página (no en cada imagen o archivo) y se
     * calcula una vez por petición.
     */
    public Choice current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        if (request.getRequestURI().startsWith("/admin")) {
            return null;
        }
        Object cached = request.getAttribute(Choice.class.getName());
        if (cached instanceof Choice choice) {
            return choice;
        }
        Choice choice = read(request);
        request.setAttribute(Choice.class.getName(), choice);
        return choice;
    }

    /** Ruta actual, para volver a ella después de elegir en el banner. */
    public String currentPath() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "/";
        }
        HttpServletRequest request = attributes.getRequest();
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null ? "" : "?" + query);
    }

    public Choice read(HttpServletRequest request) {
        Optional<Integer> version = legalTexts.current(LegalTextKind.COOKIE_POLICY).map(LegalText::getVersionNumber);
        if (version.isEmpty()) {
            return new Choice(null, false, false);
        }
        Optional<String> stored = Optional.ofNullable(request.getCookies()).stream().flatMap(Arrays::stream)
                .filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst();
        String prefix = version.get() + ".";
        if (stored.isEmpty() || !stored.get().startsWith(prefix)) {
            // Sin herramienta externa no hay cookies opcionales: no hay nada que consentir ni banner que mostrar.
            return new Choice(version.get(), analytics.hasExternalScript(), false);
        }
        return new Choice(version.get(), false, stored.get().equals(prefix + "1"));
    }

    /** Guarda la elección sobre la versión vigente. Rechazar es tan fácil como aceptar: un solo botón. */
    public void write(HttpServletRequest request, HttpServletResponse response, boolean analytics) {
        Integer version = legalTexts.current(LegalTextKind.COOKIE_POLICY).map(LegalText::getVersionNumber).orElse(0);
        ResponseCookie cookie = ResponseCookie.from(COOKIE, version + "." + (analytics ? "1" : "0"))
                .path("/")
                .maxAge(LIFETIME)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
