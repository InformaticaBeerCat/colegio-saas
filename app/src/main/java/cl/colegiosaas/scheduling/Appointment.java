package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.shared.security.SecureTokens;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Cita reservada por una familia (AGE-03). La familia la reprograma o cancela con un enlace
 * que lleva un token secreto (AGE-05); en la base solo se guarda el hash del token.
 * Horas en hora local del colegio, como el calendario.
 */
@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Setter(AccessLevel.NONE)
    private AppointmentType appointmentType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Setter(AccessLevel.NONE)
    private UserAccount host;

    @Setter(AccessLevel.NONE)
    private LocalDateTime startsAt;

    @Setter(AccessLevel.NONE)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private MeetingMode mode;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Convert(converter = EncryptedStringConverter.class)
    @Setter(AccessLevel.NONE)
    private String contactName;

    @Convert(converter = EncryptedStringConverter.class)
    @Setter(AccessLevel.NONE)
    private String contactEmail;

    @Setter(AccessLevel.NONE)
    private String contactEmailHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Setter(AccessLevel.NONE)
    private String contactPhone;

    /** Estudiante sobre el que trata la entrevista, si aplica. */
    @Convert(converter = EncryptedStringConverter.class)
    private String studentName;

    @Setter(AccessLevel.NONE)
    private String manageTokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Setter(AccessLevel.NONE)
    private ConsentRecord consent;

    /** AGE-10: solo notas breves y no sensibles (asistió, pidió reagendar…). */
    @Size(max = 1000)
    private String staffNotes;

    @Setter(AccessLevel.NONE)
    private Instant cancelledAt;

    @Setter(AccessLevel.NONE)
    private String cancellationReason;

    private Instant reminderSentAt;

    /** Resultado de reservar: la cita y el token para el enlace del correo de confirmación. */
    public record Booking(Appointment appointment, String manageToken) {
    }

    public static Booking book(AppointmentType type, UserAccount host, LocalDateTime startsAt, MeetingMode mode,
                               DataSubject contact, String phone, ConsentRecord consent, BlindIndex index) {
        if (!type.allows(mode)) {
            throw new IllegalArgumentException("Este tipo de cita no admite la modalidad " + mode);
        }
        if (!type.isHostedBy(host)) {
            throw new IllegalArgumentException("El funcionario no atiende este tipo de cita");
        }
        Appointment appointment = new Appointment();
        appointment.appointmentType = type;
        appointment.host = host;
        appointment.mode = mode;
        appointment.contactName = contact.name();
        appointment.contactEmail = contact.email();
        appointment.contactEmailHash = index.of(contact.email());
        appointment.contactPhone = phone;
        appointment.consent = Objects.requireNonNull(consent, "consent");
        appointment.placeAt(startsAt);
        String token = appointment.reissueManageToken();
        return new Booking(appointment, token);
    }

    /** Invalida el enlace anterior y entrega uno nuevo (p. ej., si la familia lo perdió). */
    public String reissueManageToken() {
        String token = SecureTokens.newToken();
        manageTokenHash = SecureTokens.hash(token);
        return token;
    }

    public boolean matchesToken(String token) {
        return token != null && SecureTokens.hash(token).equals(manageTokenHash);
    }

    public void reschedule(LocalDateTime newStart) {
        requireConfirmed();
        placeAt(newStart);
    }

    public void cancel(String reason, Instant at) {
        requireConfirmed();
        status = AppointmentStatus.CANCELLED;
        cancellationReason = reason;
        cancelledAt = at;
    }

    public void markAttended() {
        requireConfirmed();
        status = AppointmentStatus.ATTENDED;
    }

    public void markNoShow() {
        requireConfirmed();
        status = AppointmentStatus.NO_SHOW;
    }

    /** Ocupa la agenda del gestor; las canceladas liberan el bloque. */
    public boolean occupies(LocalDateTime from, LocalDateTime to) {
        return status != AppointmentStatus.CANCELLED && startsAt.isBefore(to) && endsAt.isAfter(from);
    }

    private void placeAt(LocalDateTime start) {
        startsAt = Objects.requireNonNull(start, "startsAt");
        endsAt = start.plus(appointmentType.duration());
    }

    private void requireConfirmed() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("La cita ya no está vigente (" + status + ")");
        }
    }
}
