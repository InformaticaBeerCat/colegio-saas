package cl.colegiosaas.security;

import cl.colegiosaas.analytics.AnalyticsProperties;
import cl.colegiosaas.identity.Permission;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.time.Clock;

/**
 * Reglas de acceso y cabeceras de seguridad (SEG-01).
 * El sitio público es abierto; todo {@code /admin} exige ingreso, y cada sección del panel además
 * exige su permiso con {@code @PreAuthorize} en el controlador.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(LoginPolicyProperties.class)
class SecurityConfig {

    /**
     * Content Security Policy: solo recursos del propio sitio, sin scripts en línea. Las imágenes
     * pueden venir de https (CDN, fase 9) y los videos embebidos solo de YouTube y Vimeo.
     *
     * @param analyticsOrigin origen del script de analítica externa configurado; nulo si no hay
     */
    static String contentSecurityPolicy(String analyticsOrigin) {
        String extra = analyticsOrigin == null ? "" : " " + analyticsOrigin;
        return String.join("; ",
                    "default-src 'self'",
                    // Una herramienta de analítica externa (REP-01) puede cargar su script y enviar sus datos, nada más.
                    "script-src 'self'" + extra,
                    "connect-src 'self'" + extra,
                    "img-src 'self' data: https:",
                    "media-src 'self' https:",
                    "frame-src https://www.youtube-nocookie.com https://www.youtube.com https://player.vimeo.com",
                    "object-src 'none'",
                    "base-uri 'self'",
                    "form-action 'self'",
                    "frame-ancestors 'none'");
    }

    @Bean
    SecurityFilterChain webSecurity(HttpSecurity http, LoginFlow loginFlow, SecurityContextRepository contextRepository,
                                    SessionRegistry sessionRegistry, LoginThrottle throttle, Clock clock,
                                    AnalyticsProperties analytics) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/password/**", "/admin/invitation/**").permitAll()
                        .requestMatchers(LoginFlow.MFA_SETUP_URL, LoginFlow.MFA_VERIFY_URL)
                        .hasAuthority(MfaPendingAuthentication.AUTHORITY)
                        .requestMatchers("/admin/**").hasAuthority(Permission.PANEL_ACCESS.name())
                        .anyRequest().permitAll())
                // El banner de cookies aparece en todas las páginas públicas: sin token CSRF, así mostrarlo no abre
                // una sesión por visitante. Lo peor que logra un sitio ajeno es cambiar esa preferencia (PRV-03).
                .csrf(csrf -> csrf.ignoringRequestMatchers(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/privacidad/cookies/preferencias")))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .usernameParameter("email")
                        .successHandler(loginFlow)
                        .failureUrl("/admin/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout"))
                .exceptionHandling(errors -> errors.accessDeniedHandler(mfaAwareAccessDenied()))
                .sessionManagement(sessions -> sessions
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/admin/login?expired"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy(analytics.scriptOrigin())))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                        // SEO-03: el panel y los enlaces personales nunca se indexan, tampoco sus PDF o imágenes.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new OrRequestMatcher(
                                PathPatternRequestMatcher.pathPattern("/admin/**"),
                                PathPatternRequestMatcher.pathPattern("/setup/**"),
                                PathPatternRequestMatcher.pathPattern("/citas/**"),
                                PathPatternRequestMatcher.pathPattern("/inscripciones/**"),
                                PathPatternRequestMatcher.pathPattern("/privacidad/derechos/estado")),
                                new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow"))))
                .addFilterBefore(new LoginThrottleFilter(throttle, clock), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Quien está a medio camino del MFA y pide otra página del panel vuelve al paso que le falta. */
    private static AccessDeniedHandler mfaAwareAccessDenied() {
        return (request, response, denied) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof MfaPendingAuthentication pending) {
                String next = pending.user().isMfaEnabled() ? LoginFlow.MFA_VERIFY_URL : LoginFlow.MFA_SETUP_URL;
                response.sendRedirect(request.getContextPath() + next);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        };
    }

    /** BCrypt con prefijo ({@code {bcrypt}…}): permite migrar a otro algoritmo sin invalidar contraseñas. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa al registro de sesiones cuando una sesión se crea, cambia de id o expira. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
