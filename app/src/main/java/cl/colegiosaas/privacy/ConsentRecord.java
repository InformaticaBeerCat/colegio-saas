package cl.colegiosaas.privacy;

import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
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
 * Evidencia de un consentimiento (PRV-04): quién, cuándo, para qué, qué versión del texto aceptó
 * y desde dónde. Solo se inserta; retirar el consentimiento marca {@code withdrawnAt} sin borrar nada.
 */
@Entity
@Table(name = "consent_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord extends BaseEntity {

    @Convert(converter = EncryptedStringConverter.class)
    private String subjectName;

    @Convert(converter = EncryptedStringConverter.class)
    private String subjectEmail;

    /** Índice ciego del email: permite encontrar todos los consentimientos de una persona (PRV-07). */
    private String subjectEmailHash;

    @Enumerated(EnumType.STRING)
    private ConsentPurpose purpose;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private LegalText legalText;

    /** También se registra el "no": prueba de que la casilla se ofreció sin premarcar (PRV-02). */
    private boolean granted;

    private String source;

    @Convert(converter = EncryptedStringConverter.class)
    private String ipAddress;

    private String userAgent;

    private Instant withdrawnAt;

    public ConsentRecord(DataSubject subject, ConsentPurpose purpose, LegalText legalText, boolean granted,
                         RequestOrigin origin, BlindIndex index) {
        if (!legalText.isPublished()) {
            throw new IllegalArgumentException("Solo se puede consentir sobre un texto legal publicado");
        }
        this.subjectName = subject.name();
        this.subjectEmail = subject.email();
        this.subjectEmailHash = index.of(subject.email());
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.legalText = legalText;
        this.granted = granted;
        this.source = origin.source();
        this.ipAddress = origin.ipAddress();
        this.userAgent = origin.userAgent();
    }

    public void withdraw() {
        if (withdrawnAt == null) {
            withdrawnAt = Instant.now();
        }
    }

    public boolean isActive() {
        return granted && withdrawnAt == null;
    }
}
