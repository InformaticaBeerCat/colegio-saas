package cl.colegiosaas.admissions;

import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.GradeLevel;
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
import lombok.Setter;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Familia interesada en el colegio (ADM-03): "quiero más información", visita, puertas abiertas.
 * Es la base del CRM de admisión (ADM-05, premium). Se borra al vencer {@code retainUntil} (PRV-06).
 */
@Entity
@Table(name = "prospect")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prospect extends BaseEntity {

    @Convert(converter = EncryptedStringConverter.class)
    @Setter(AccessLevel.NONE)
    private String guardianName;

    @Convert(converter = EncryptedStringConverter.class)
    @Setter(AccessLevel.NONE)
    private String email;

    @Setter(AccessLevel.NONE)
    private String emailHash;

    @Convert(converter = EncryptedStringConverter.class)
    private String phone;

    /** Nivel al que postularía. */
    @ManyToOne(fetch = FetchType.LAZY)
    private GradeLevel gradeLevel;

    private Integer entryYear;

    @Enumerated(EnumType.STRING)
    private ProspectSource source;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private ProspectStage stage = ProspectStage.INTERESTED;

    /** Notas del equipo de admisión. Columna LONGTEXT. */
    @Convert(converter = EncryptedStringConverter.class)
    private String notes;

    /** Aceptación del aviso de tratamiento del formulario de admisión. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Setter(AccessLevel.NONE)
    private ConsentRecord consent;

    /** Consentimiento aparte para correos de seguimiento (ADM-06); nulo = no se le escribe. */
    @ManyToOne(fetch = FetchType.LAZY)
    private ConsentRecord followUpConsent;

    @Setter(AccessLevel.NONE)
    private LocalDate retainUntil;

    private String utmSource;

    private String utmCampaign;

    public Prospect(DataSubject guardian, String phone, GradeLevel gradeLevel, Integer entryYear,
                    ProspectSource source, ConsentRecord consent, LocalDate retainUntil, BlindIndex index) {
        this.guardianName = guardian.name();
        this.email = guardian.email();
        this.emailHash = index.of(guardian.email());
        this.phone = phone;
        this.gradeLevel = gradeLevel;
        this.entryYear = entryYear;
        this.source = source;
        this.consent = Objects.requireNonNull(consent, "consent");
        this.retainUntil = Objects.requireNonNull(retainUntil, "retainUntil");
    }

    /** El embudo solo avanza; descartar se puede desde cualquier etapa salvo matriculado. */
    public void advanceTo(ProspectStage next) {
        if (stage == ProspectStage.ENROLLED || stage == ProspectStage.DISCARDED) {
            throw new IllegalStateException("El prospecto ya está cerrado (" + stage + ")");
        }
        if (next != ProspectStage.DISCARDED && next.ordinal() <= stage.ordinal()) {
            throw new IllegalArgumentException("El embudo no retrocede: " + stage + " → " + next);
        }
        stage = next;
    }

    public void extendRetention(LocalDate until) {
        if (until.isBefore(retainUntil)) {
            throw new IllegalArgumentException("Para acortar el plazo se elimina el registro, no se edita");
        }
        retainUntil = until;
    }

    public boolean isExpired(LocalDate today) {
        return today.isAfter(retainUntil);
    }

    public boolean acceptsFollowUp() {
        return followUpConsent != null && followUpConsent.isActive();
    }
}
