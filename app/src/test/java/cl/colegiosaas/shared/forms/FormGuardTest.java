package cl.colegiosaas.shared.forms;

import cl.colegiosaas.shared.crypto.BlindIndex;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static cl.colegiosaas.shared.forms.FormGuard.Verdict.BOT;
import static cl.colegiosaas.shared.forms.FormGuard.Verdict.EXPIRED;
import static cl.colegiosaas.shared.forms.FormGuard.Verdict.OK;
import static cl.colegiosaas.shared.forms.FormGuard.Verdict.TOO_MANY;
import static org.assertj.core.api.Assertions.assertThat;

class FormGuardTest {

    static final BlindIndex SIGNER = new BlindIndex(new byte[32]);
    static final FormGuardProperties RULES = new FormGuardProperties(Duration.ofSeconds(3), Duration.ofDays(2), 2, Duration.ofMinutes(10));
    final Instant shown = Instant.parse("2026-10-07T12:00:00Z");

    FormGuard at(Instant now) {
        return new FormGuard(RULES, SIGNER, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void humansPassAndBotsAreSpottedWithoutTellingThem() {
        String stamp = at(shown).stamp();
        FormGuard later = at(shown.plusSeconds(20));

        assertThat(later.check("contacto", "1.1.1.1", null, stamp)).isEqualTo(OK);
        assertThat(later.check("contacto", "1.1.1.2", "http://spam", stamp)).isEqualTo(BOT);
        assertThat(later.check("contacto", "1.1.1.3", null, null)).isEqualTo(BOT);
        assertThat(later.check("contacto", "1.1.1.4", null, "123.falso")).isEqualTo(BOT);
        // Cambiar la hora del sello invalida la firma.
        assertThat(later.check("contacto", "1.1.1.5", null, (shown.getEpochSecond() - 60) + stamp.substring(stamp.indexOf('.')))).isEqualTo(BOT);
        assertThat(at(shown.plusSeconds(1)).check("contacto", "1.1.1.6", null, stamp)).isEqualTo(BOT);
        assertThat(at(shown.plus(Duration.ofDays(3))).check("contacto", "1.1.1.7", null, stamp)).isEqualTo(EXPIRED);
    }

    @Test
    void eachFormLimitsSubmissionsPerIp() {
        String stamp = at(shown).stamp();
        FormGuard guard = at(shown.plusSeconds(20));
        assertThat(guard.check("contacto", "2.2.2.2", null, stamp)).isEqualTo(OK);
        assertThat(guard.check("contacto", "2.2.2.2", null, stamp)).isEqualTo(OK);
        assertThat(guard.check("contacto", "2.2.2.2", null, stamp)).isEqualTo(TOO_MANY);
        assertThat(guard.check("cita", "2.2.2.2", null, stamp)).isEqualTo(OK);
        assertThat(guard.check("contacto", "3.3.3.3", null, stamp)).isEqualTo(OK);
    }
}
