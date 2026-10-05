package cl.colegiosaas.security;

import cl.colegiosaas.identity.Permission;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

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
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "img-src 'self' data: https:",
            "media-src 'self' https:",
            "frame-src https://www.youtube-nocookie.com https://www.youtube.com https://player.vimeo.com",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain webSecurity(HttpSecurity http, LoginFlow loginFlow, SecurityContextRepository contextRepository,
                                    SessionRegistry sessionRegistry, LoginThrottle throttle, Clock clock) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/password/**", "/admin/invitation/**").permitAll()
                        .requestMatchers(LoginFlow.MFA_SETUP_URL, LoginFlow.MFA_VERIFY_URL)
                        .hasAuthority(MfaPendingAuthentication.AUTHORITY)
                        .requestMatchers("/admin/**").hasAuthority(Permission.PANEL_ACCESS.name())
                        .anyRequest().permitAll())
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
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
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
