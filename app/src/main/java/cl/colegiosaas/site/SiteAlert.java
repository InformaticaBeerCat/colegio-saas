package cl.colegiosaas.site;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Banner global en todo el sitio (PUB-12): suspensión de clases, emergencia, simulacro.
 * Se activa en un clic y puede tener ventana de tiempo para apagarse sola.
 */
@Entity
@Table(name = "site_alert")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteAlert extends BaseEntity {

    @NotBlank
    @Size(max = 300)
    private String message;

    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    private String linkUrl;

    private String linkLabel;

    @Setter(AccessLevel.NONE)
    private boolean active;

    /** Nulo = visible desde que se activa. */
    private Instant startsAt;

    /** Nulo = visible hasta que alguien la desactive. */
    private Instant endsAt;

    public SiteAlert(String message, AlertSeverity severity) {
        this.message = message;
        this.severity = severity;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public boolean isVisibleAt(Instant now) {
        return active
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || endsAt.isAfter(now));
    }
}
