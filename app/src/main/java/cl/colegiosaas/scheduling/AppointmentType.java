package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Tipo de cita configurable: visita guiada, entrevista con profesor jefe… (AGE-01). */
@Entity
@Table(name = "appointment_type")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppointmentType extends BaseEntity {

    @NotBlank
    private String name;

    private String description;

    @Setter(AccessLevel.NONE)
    private int durationMinutes;

    /** Pausa entre una cita y la siguiente. */
    private int bufferMinutes;

    @Enumerated(EnumType.STRING)
    private AppointmentAudience audience;

    @Setter(AccessLevel.NONE)
    private boolean inPersonAllowed = true;

    @Setter(AccessLevel.NONE)
    private boolean onlineAllowed;

    private boolean active = true;

    /** Funcionarios que atienden este tipo de cita (rol SCHEDULE_MANAGER). */
    @ManyToMany
    @JoinTable(name = "appointment_type_host",
            joinColumns = @JoinColumn(name = "appointment_type_id"),
            inverseJoinColumns = @JoinColumn(name = "user_account_id"))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<UserAccount> hosts = new HashSet<>();

    public AppointmentType(String name, int durationMinutes, AppointmentAudience audience) {
        this.name = name;
        this.audience = audience;
        changeDuration(durationMinutes);
    }

    public void changeDuration(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor que cero");
        }
        durationMinutes = minutes;
    }

    public void allowModes(boolean inPerson, boolean online) {
        if (!inPerson && !online) {
            throw new IllegalArgumentException("La cita debe ser presencial, en línea o ambas");
        }
        inPersonAllowed = inPerson;
        onlineAllowed = online;
    }

    public boolean allows(MeetingMode mode) {
        return mode == MeetingMode.IN_PERSON ? inPersonAllowed : onlineAllowed;
    }

    public Duration duration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public void addHost(UserAccount host) {
        hosts.add(host);
    }

    public void removeHost(UserAccount host) {
        hosts.remove(host);
    }

    public boolean isHostedBy(UserAccount user) {
        return hosts.contains(user);
    }

    public Set<UserAccount> getHosts() {
        return Collections.unmodifiableSet(hosts);
    }
}
