package cl.colegiosaas.security;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.shared.security.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;

/** Enrolamiento y verificación del segundo factor (USR-02). */
@Service
public class MfaService {

    static final int RECOVERY_CODES = 10;

    private final UserAccountRepository users;
    private final AuditTrail audit;
    private final Clock clock;

    MfaService(UserAccountRepository users, AuditTrail audit, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Activa el MFA si {@code code} calza con el secreto recién mostrado: así se comprueba que la app
     * quedó bien configurada antes de exigirla. Devuelve los códigos de recuperación, que se muestran una vez.
     */
    @Transactional
    public List<String> enroll(long userId, String secret, String code) {
        Instant now = clock.instant();
        OptionalLong step = Totp.matchingStep(secret, clean(code), now);
        if (step.isEmpty()) {
            throw new InvalidMfaCodeException();
        }
        List<String> codes = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String raw = SecureTokens.newCode("", 10);
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
            hashes.add(hashRecoveryCode(raw));
        }
        UserAccount user = users.findById(userId).orElseThrow();
        user.enableMfa(secret, step.getAsLong(), hashes, now);
        audit.recordFor(user, AuditAction.MFA_ENABLED, "UserAccount", user.getId(), null);
        return codes;
    }

    /** Acepta un código TOTP de 6 dígitos o un código de recuperación (cada uno, una sola vez). */
    @Transactional
    public boolean verify(long userId, String input) {
        UserAccount user = users.findById(userId).orElseThrow();
        String code = clean(input);
        if (code.length() == 10) {
            return user.consumeRecoveryCode(hashRecoveryCode(code));
        }
        OptionalLong step = Totp.matchingStep(user.mfaSecret(), code, clock.instant());
        return step.isPresent() && user.consumeTotpStep(step.getAsLong());
    }

    static String hashRecoveryCode(String code) {
        return SecureTokens.hash(clean(code));
    }

    private static String clean(String input) {
        return input == null ? "" : input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static class InvalidMfaCodeException extends RuntimeException {
        InvalidMfaCodeException() {
            super("El código no es válido. Revisa que la hora del teléfono esté bien.");
        }
    }
}
