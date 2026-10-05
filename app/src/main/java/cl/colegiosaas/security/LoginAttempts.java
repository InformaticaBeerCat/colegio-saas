package cl.colegiosaas.security;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** Lleva la cuenta de ingresos exitosos y fallidos, bloquea y deja todo en la auditoría. */
@Service
public class LoginAttempts {

    private final UserAccountRepository users;
    private final LoginThrottle throttle;
    private final LoginPolicyProperties policy;
    private final AuditTrail audit;
    private final Clock clock;

    LoginAttempts(UserAccountRepository users, LoginThrottle throttle, LoginPolicyProperties policy,
                  AuditTrail audit, Clock clock) {
        this.users = users;
        this.throttle = throttle;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    /** Contraseña equivocada (o cuenta inexistente, bloqueada o deshabilitada). */
    @Transactional
    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        throttle.recordFailure(ip, now);
        users.findByEmail(email == null ? "" : email.strip().toLowerCase(Locale.ROOT)).ifPresentOrElse(
                user -> registerFailure(user, now, "Contraseña incorrecta"),
                // No se guarda el email intentado: puede ser un dato personal de alguien ajeno.
                () -> audit.recordAnonymous(AuditAction.LOGIN_FAILED, "UserAccount", null, "Correo no registrado"));
    }

    /** Código MFA equivocado. Devuelve true si con esto la cuenta quedó bloqueada. */
    @Transactional
    public boolean recordMfaFailure(long userId, String ip) {
        Instant now = clock.instant();
        throttle.recordFailure(ip, now);
        UserAccount user = users.findById(userId).orElseThrow();
        return registerFailure(user, now, "Código de verificación incorrecto");
    }

    @Transactional
    public void recordSuccess(long userId) {
        UserAccount user = users.findById(userId).orElseThrow();
        user.recordSuccessfulLogin(clock.instant());
        audit.recordFor(user, AuditAction.LOGIN, "UserAccount", user.getId(), null);
    }

    private boolean registerFailure(UserAccount user, Instant now, String reason) {
        boolean locked = user.recordFailedLogin(now, policy.maxFailedLogins(), policy.lockoutDuration());
        audit.recordFor(user, AuditAction.LOGIN_FAILED, "UserAccount", user.getId(), reason);
        if (locked) {
            audit.recordFor(user, AuditAction.ACCOUNT_LOCKED, "UserAccount", user.getId(),
                    "Bloqueada por " + policy.lockoutDuration().toMinutes() + " minutos");
        }
        return locked;
    }
}
