package cl.colegiosaas.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    final LoginThrottle throttle = new LoginThrottle(
            new LoginPolicyProperties(5, Duration.ofMinutes(15), 3, Duration.ofMinutes(10)));
    final Instant now = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void blocksAnIpAfterTooManyFailuresWithinTheWindow() {
        throttle.recordFailure("200.1.1.1", now);
        throttle.recordFailure("200.1.1.1", now.plusSeconds(10));
        assertThat(throttle.isBlocked("200.1.1.1", now.plusSeconds(20))).isFalse();

        throttle.recordFailure("200.1.1.1", now.plusSeconds(30));
        assertThat(throttle.isBlocked("200.1.1.1", now.plusSeconds(40))).isTrue();
        assertThat(throttle.isBlocked("200.9.9.9", now.plusSeconds(40))).isFalse();
    }

    @Test
    void oldFailuresExpire() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("200.1.1.1", now);
        }
        assertThat(throttle.isBlocked("200.1.1.1", now.plus(Duration.ofMinutes(11)))).isFalse();
    }
}
