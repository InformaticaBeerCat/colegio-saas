package cl.colegiosaas.site;

import cl.colegiosaas.support.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class SiteAlertRepositoryTest {

    @Autowired
    SiteAlertRepository alerts;

    final Instant now = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void onlyActiveAlertsWithinTheirWindowAreVisible() {
        SiteAlert suspension = alert("Clases suspendidas por lluvia", null, null);
        SiteAlert expired = alert("Simulacro de ayer", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        SiteAlert future = alert("Corte de agua mañana", now.plus(Duration.ofHours(12)), null);
        SiteAlert inactive = new SiteAlert("Borrador", AlertSeverity.INFO);
        alerts.saveAllAndFlush(List.of(suspension, expired, future, inactive));

        assertThat(alerts.findVisibleAt(now))
                .extracting(SiteAlert::getMessage)
                .containsExactly("Clases suspendidas por lluvia");

        // La regla en memoria y la consulta deben coincidir.
        assertThat(suspension.isVisibleAt(now)).isTrue();
        assertThat(expired.isVisibleAt(now)).isFalse();
        assertThat(future.isVisibleAt(now)).isFalse();
        assertThat(inactive.isVisibleAt(now)).isFalse();
    }

    private SiteAlert alert(String message, Instant startsAt, Instant endsAt) {
        SiteAlert alert = new SiteAlert(message, AlertSeverity.WARNING);
        alert.setStartsAt(startsAt);
        alert.setEndsAt(endsAt);
        alert.activate();
        return alert;
    }
}
