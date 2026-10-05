package cl.colegiosaas.identity;

import java.time.Duration;

public enum AccountTokenPurpose {
    INVITATION(Duration.ofDays(7)),
    PASSWORD_RESET(Duration.ofHours(1));

    private final Duration validity;

    AccountTokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}
