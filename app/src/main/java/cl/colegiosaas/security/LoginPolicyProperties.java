package cl.colegiosaas.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Límites contra fuerza bruta (SEG-06).
 *
 * @param maxFailedLogins      intentos fallidos seguidos antes de bloquear la cuenta
 * @param lockoutDuration      cuánto dura ese bloqueo
 * @param maxFailedLoginsPerIp intentos fallidos desde una misma IP dentro de {@code ipWindow}
 * @param ipWindow             ventana de tiempo para contar los de una IP
 */
@ConfigurationProperties("app.security")
public record LoginPolicyProperties(int maxFailedLogins, Duration lockoutDuration,
                                    int maxFailedLoginsPerIp, Duration ipWindow) {
}
