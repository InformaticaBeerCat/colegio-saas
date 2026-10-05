package cl.colegiosaas.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountSecurityTest {

    final Instant now = Instant.parse("2026-10-05T12:00:00Z");
    final Duration lock = Duration.ofMinutes(15);

    @Test
    void locksAfterTooManyFailuresAndUnlocksWithTime() {
        UserAccount user = activeUser();
        for (int i = 0; i < 4; i++) {
            assertThat(user.recordFailedLogin(now, 5, lock)).isFalse();
        }
        assertThat(user.recordFailedLogin(now, 5, lock)).isTrue();

        assertThat(user.isLockedAt(now.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLockedAt(now.plus(Duration.ofMinutes(16)))).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        UserAccount user = activeUser();
        user.recordFailedLogin(now, 5, lock);
        user.recordFailedLogin(now, 5, lock);

        user.recordSuccessfulLogin(now);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(now);
    }

    @Test
    void totpStepsAndRecoveryCodesWorkOnlyOnce() {
        UserAccount user = activeUser();
        user.enableMfa("SECRET", 100, Set.of("hash-1", "hash-2"), now);

        assertThat(user.consumeTotpStep(100)).isFalse();
        assertThat(user.consumeTotpStep(101)).isTrue();
        assertThat(user.consumeTotpStep(101)).isFalse();

        assertThat(user.consumeRecoveryCode("hash-1")).isTrue();
        assertThat(user.consumeRecoveryCode("hash-1")).isFalse();
        assertThat(user.remainingRecoveryCodes()).isEqualTo(1);
    }

    @Test
    void deactivationRemovesEveryWayIn() {
        UserAccount user = activeUser();
        user.enableMfa("SECRET", 1, Set.of("hash"), now);

        user.deactivate();

        assertThat(user.canLogIn()).isFalse();
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.getPasswordHash()).isNull();
    }

    private UserAccount activeUser() {
        UserAccount user = new UserAccount("ana@colegio.cl", "Ana", Role.SCHOOL_ADMIN);
        user.changePassword("{noop}x");
        user.activate();
        return user;
    }
}
