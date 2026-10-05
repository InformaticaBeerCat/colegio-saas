package cl.colegiosaas.calendar;

import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.shared.security.SecureTokens;
import cl.colegiosaas.structure.Course;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * Inscripción a un evento (EVE-02) o confirmación de asistencia a una reunión de apoderados (AGE-08).
 * Si no hay cupo queda en lista de espera; quien decide es el servicio, que cuenta los cupos con bloqueo.
 */
@Entity
@Table(name = "event_registration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRegistration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Event event;

    @Convert(converter = EncryptedStringConverter.class)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    private String emailHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String phone;

    /** Personas que vienen con esta inscripción. */
    private int attendees;

    /** Curso del estudiante, en reuniones de apoderados. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Course course;

    @Convert(converter = EncryptedStringConverter.class)
    private String studentName;

    @Enumerated(EnumType.STRING)
    private RegistrationStatus status;

    private Integer waitlistPosition;

    private String manageTokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ConsentRecord consent;

    private Instant cancelledAt;

    /** Datos que entrega quien se inscribe. */
    public record Registrant(DataSubject person, String phone, int attendees, Course course, String studentName) {
        public Registrant {
            if (attendees < 1) {
                throw new IllegalArgumentException("Debe asistir al menos una persona");
            }
        }
    }

    /** Resultado de inscribirse: la inscripción y el token para el enlace de cancelación. */
    public record Created(EventRegistration registration, String manageToken) {
    }

    public static Created confirmed(Event event, Registrant registrant, ConsentRecord consent, BlindIndex index) {
        return create(event, registrant, consent, index, RegistrationStatus.CONFIRMED, null);
    }

    public static Created waitlisted(Event event, Registrant registrant, int position, ConsentRecord consent, BlindIndex index) {
        if (!event.isWaitlistEnabled()) {
            throw new IllegalStateException("El evento no tiene lista de espera");
        }
        return create(event, registrant, consent, index, RegistrationStatus.WAITLISTED, position);
    }

    private static Created create(Event event, Registrant registrant, ConsentRecord consent, BlindIndex index,
                                  RegistrationStatus status, Integer position) {
        EventRegistration registration = new EventRegistration();
        registration.event = Objects.requireNonNull(event, "event");
        registration.name = registrant.person().name();
        registration.email = registrant.person().email();
        registration.emailHash = index.of(registrant.person().email());
        registration.phone = registrant.phone();
        registration.attendees = registrant.attendees();
        registration.course = registrant.course();
        registration.studentName = registrant.studentName();
        registration.consent = Objects.requireNonNull(consent, "consent");
        registration.status = status;
        registration.waitlistPosition = position;
        String token = SecureTokens.newToken();
        registration.manageTokenHash = SecureTokens.hash(token);
        return new Created(registration, token);
    }

    public boolean matchesToken(String token) {
        return token != null && SecureTokens.hash(token).equals(manageTokenHash);
    }

    /** Se liberó un cupo: pasa de la lista de espera a confirmada. */
    public void promoteFromWaitlist() {
        if (status != RegistrationStatus.WAITLISTED) {
            throw new IllegalStateException("Solo se promueve una inscripción en lista de espera");
        }
        status = RegistrationStatus.CONFIRMED;
        waitlistPosition = null;
    }

    public void cancel(Instant at) {
        if (status == RegistrationStatus.CANCELLED) {
            return;
        }
        status = RegistrationStatus.CANCELLED;
        waitlistPosition = null;
        cancelledAt = at;
    }

    public void markAttended() {
        status = RegistrationStatus.ATTENDED;
    }
}
