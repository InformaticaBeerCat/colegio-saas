package cl.colegiosaas.contact;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.privacy.Anonymized;
import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.shared.security.SecureTokens;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Consulta del formulario de contacto, enrutada a un área y con número de ticket (COM-01).
 * Todo lo que escribe la persona va cifrado: puede incluir datos sensibles sin que lo sepamos.
 */
@Entity
@Table(name = "inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

    /** Número de ticket que recibe la familia, p. ej. "C-7KQ2M9XA". */
    private String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ContactArea area;

    @Convert(converter = EncryptedStringConverter.class)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    private String emailHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String phone;

    @Convert(converter = EncryptedStringConverter.class)
    private String subject;

    /** Columna LONGTEXT. */
    @Convert(converter = EncryptedStringConverter.class)
    private String message;

    @Enumerated(EnumType.STRING)
    private InquiryStatus status = InquiryStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount assignedTo;

    private Instant firstResponseAt;

    private Instant resolvedAt;

    /** Aceptación del aviso de tratamiento del formulario de contacto (PRV-01). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ConsentRecord consent;

    @OneToMany(mappedBy = "inquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Getter(AccessLevel.NONE)
    private List<InquiryNote> notes = new ArrayList<>();

    public Inquiry(ContactArea area, DataSubject sender, String phone, String subject, String message,
                   ConsentRecord consent, BlindIndex index) {
        this.ticketCode = SecureTokens.newCode("C-", 8);
        this.area = Objects.requireNonNull(area, "area");
        this.name = sender.name();
        this.email = sender.email();
        this.emailHash = index.of(sender.email());
        this.phone = phone;
        this.subject = subject;
        this.message = Objects.requireNonNull(message, "message");
        this.consent = Objects.requireNonNull(consent, "consent");
    }

    /** Reasignar de área (la familia eligió mal) mantiene el ticket. */
    public void routeTo(ContactArea newArea) {
        area = Objects.requireNonNull(newArea, "area");
    }

    public void assignTo(UserAccount staff) {
        assignedTo = staff;
        if (status == InquiryStatus.NEW) {
            status = InquiryStatus.IN_PROGRESS;
        }
    }

    /** Se llama al responder a la familia; la primera respuesta mide el tiempo de atención (COM-02). */
    public void recordResponse(Instant at) {
        if (firstResponseAt == null) {
            firstResponseAt = at;
        }
        if (status == InquiryStatus.NEW) {
            status = InquiryStatus.IN_PROGRESS;
        }
    }

    public void resolve(Instant at) {
        status = InquiryStatus.RESOLVED;
        resolvedAt = at;
    }

    public void markAsSpam() {
        status = InquiryStatus.SPAM;
    }

    public void reopen() {
        status = InquiryStatus.IN_PROGRESS;
        resolvedAt = null;
    }

    public Optional<Duration> timeToFirstResponse() {
        return Optional.ofNullable(firstResponseAt).map(at -> Duration.between(getCreatedAt(), at));
    }

    /** Conserva la fila (área, fechas, tiempos de respuesta) para estadísticas y borra lo que identifica (PRV-06). */
    public void anonymize() {
        name = null;
        email = Anonymized.EMAIL;
        emailHash = Anonymized.EMAIL_HASH;
        phone = null;
        subject = null;
        message = Anonymized.TEXT;
        notes.clear();
    }

    public boolean isAnonymized() {
        return Anonymized.isAnonymized(emailHash);
    }

    public InquiryNote addNote(UserAccount author, String body) {
        InquiryNote note = new InquiryNote(this, author, body);
        notes.add(note);
        return note;
    }

    public List<InquiryNote> getNotes() {
        return Collections.unmodifiableList(notes);
    }
}
