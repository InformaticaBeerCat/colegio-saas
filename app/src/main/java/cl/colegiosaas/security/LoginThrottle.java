package cl.colegiosaas.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta intentos fallidos por IP en memoria (SEG-06). Complementa el bloqueo por cuenta: frena a
 * quien prueba muchas cuentas distintas desde la misma IP. Una instancia por colegio: memoria basta.
 */
@Component
public class LoginThrottle {

    private final LoginPolicyProperties policy;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    LoginThrottle(LoginPolicyProperties policy) {
        this.policy = policy;
    }

    public void recordFailure(String ip, Instant now) {
        if (ip == null) {
            return;
        }
        Deque<Instant> attempts = failures.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (attempts) {
            attempts.addLast(now);
            prune(attempts, now);
        }
    }

    public boolean isBlocked(String ip, Instant now) {
        Deque<Instant> attempts = ip == null ? null : failures.get(ip);
        if (attempts == null) {
            return false;
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.isEmpty()) {
                failures.remove(ip, attempts);
            }
            return attempts.size() >= policy.maxFailedLoginsPerIp();
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant limit = now.minus(policy.ipWindow());
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(limit)) {
            attempts.removeFirst();
        }
    }
}
