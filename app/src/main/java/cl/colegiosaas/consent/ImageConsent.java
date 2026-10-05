package cl.colegiosaas.consent;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
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
 * Autorización de uso de imagen de un estudiante para un canal (Ley 21.719 y 21.430).
 * Específica y revocable: revocar marca {@code revokedAt}; volver a autorizar crea otra fila.
 * Así queda la historia completa y la vigente es la que no está revocada.
 */
@Entity
@Table(name = "image_consent")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageConsent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    private ConsentChannel channel;

    /** Apoderado que autorizó. */
    @Convert(converter = EncryptedStringConverter.class)
    private String grantedByName;

    @Enumerated(EnumType.STRING)
    private ConsentMethod method;

    /** Formulario firmado escaneado, cuando la autorización fue en papel. */
    @ManyToOne(fetch = FetchType.LAZY)
    private StoredFile evidenceFile;

    /** Versión del formulario de autorización que se firmó. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private LegalText legalText;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount recordedBy;

    private Instant grantedAt;

    private Instant revokedAt;

    private String revocationNote;

    public ImageConsent(Student student, ConsentChannel channel, String grantedByName, ConsentMethod method,
                        LegalText legalText, StoredFile evidenceFile, UserAccount recordedBy, Instant grantedAt) {
        if (legalText.getKind() != LegalTextKind.IMAGE_CONSENT_FORM || !legalText.isPublished()) {
            throw new IllegalArgumentException("La autorización debe referir al formulario de imagen publicado");
        }
        this.student = Objects.requireNonNull(student, "student");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.grantedByName = grantedByName;
        this.method = method;
        this.legalText = legalText;
        this.evidenceFile = evidenceFile;
        this.recordedBy = recordedBy;
        this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
    }

    /** Tras revocar, el gestor debe retirar las fotos donde aparece el estudiante (MED-09). */
    public void revoke(String note) {
        if (revokedAt != null) {
            throw new IllegalStateException("La autorización ya estaba revocada");
        }
        revokedAt = Instant.now();
        revocationNote = note;
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
