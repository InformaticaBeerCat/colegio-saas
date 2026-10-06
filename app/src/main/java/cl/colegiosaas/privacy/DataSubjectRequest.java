package cl.colegiosaas.privacy;

import cl.colegiosaas.identity.UserAccount;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Solicitud de ejercicio de derechos (PRV-05) con seguimiento de plazo.
 * El plazo legal lo calcula el servicio desde la configuración (pendiente de validar con abogado).
 */
@Entity
@Table(name = "data_subject_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataSubjectRequest extends BaseEntity {

    /** Código que recibe el solicitante para consultar el estado, p. ej. "D-7KQ2M9XA". */
    private String trackingCode;

    /** "right" es palabra reservada en SQL. */
    @Enumerated(EnumType.STRING)
    private DataSubjectRight requestedRight;

    @Convert(converter = EncryptedStringConverter.class)
    private String requesterName;

    @Convert(converter = EncryptedStringConverter.class)
    private String requesterEmail;

    private String requesterEmailHash;

    /** El apoderado ejerce el derecho en nombre de un estudiante menor de edad. */
    private boolean onBehalfOfMinor;

    /** Columna LONGTEXT. */
    @Convert(converter = EncryptedStringConverter.class)
    private String details;

    @Enumerated(EnumType.STRING)
    private DataSubjectRequestStatus status = DataSubjectRequestStatus.RECEIVED;

    private LocalDate dueOn;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount handledBy;

    private Instant resolvedAt;

    private String resolution;

    public DataSubjectRequest(DataSubjectRight right, DataSubject requester, boolean onBehalfOfMinor,
                              String details, LocalDate dueOn, BlindIndex index) {
        this.trackingCode = SecureTokens.newCode("D-", 8);
        this.requestedRight = Objects.requireNonNull(right, "right");
        this.requesterName = requester.name();
        this.requesterEmail = requester.email();
        this.requesterEmailHash = index.of(requester.email());
        this.onBehalfOfMinor = onBehalfOfMinor;
        this.details = details;
        this.dueOn = Objects.requireNonNull(dueOn, "dueOn");
    }

    public void requestIdentityVerification() {
        requireOpen();
        status = DataSubjectRequestStatus.VERIFYING_IDENTITY;
    }

    public void startProcessing(UserAccount handler) {
        requireOpen();
        handledBy = Objects.requireNonNull(handler, "handler");
        status = DataSubjectRequestStatus.IN_PROGRESS;
    }

    public void complete(String resolutionText) {
        close(DataSubjectRequestStatus.COMPLETED, resolutionText);
    }

    /** Rechazo fundado (p. ej., identidad no acreditada); la fundamentación queda registrada. */
    public void reject(String reason) {
        close(DataSubjectRequestStatus.REJECTED, reason);
    }

    public boolean isOverdue(LocalDate today) {
        return status.isOpen() && today.isAfter(dueOn);
    }

    /** Al vencer el plazo de conservación queda solo el registro de que se atendió en plazo (PRV-06). */
    public void anonymize() {
        requesterName = null;
        requesterEmail = Anonymized.EMAIL;
        requesterEmailHash = Anonymized.EMAIL_HASH;
        details = null;
    }

    public boolean isAnonymized() {
        return Anonymized.isAnonymized(requesterEmailHash);
    }

    private void close(DataSubjectRequestStatus finalStatus, String text) {
        requireOpen();
        status = finalStatus;
        resolution = text;
        resolvedAt = Instant.now();
    }

    private void requireOpen() {
        if (!status.isOpen()) {
            throw new IllegalStateException("La solicitud ya está cerrada");
        }
    }
}
