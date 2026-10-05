package cl.colegiosaas.info;

import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Lista de útiles, uniforme o minuta (PUB-10): con vista web ({@code content}),
 * descargable ({@code file}) o ambas.
 */
@Entity
@Table(name = "info_sheet")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InfoSheet extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private InfoSheetKind kind;

    @NotBlank
    private String title;

    /** Nulo = aplica a todos los niveles. */
    @ManyToOne(fetch = FetchType.LAZY)
    private GradeLevel gradeLevel;

    private int academicYear;

    /** HTML para la vista web. Columna LONGTEXT. */
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    private StoredFile file;

    /** Útil para la minuta mensual. */
    private LocalDate validFrom;

    private LocalDate validUntil;

    private boolean published;

    public InfoSheet(InfoSheetKind kind, String title, int academicYear) {
        this.kind = kind;
        this.title = title;
        this.academicYear = academicYear;
    }

    public boolean isCurrentOn(LocalDate day) {
        return published
                && (validFrom == null || !validFrom.isAfter(day))
                && (validUntil == null || !validUntil.isBefore(day));
    }
}
