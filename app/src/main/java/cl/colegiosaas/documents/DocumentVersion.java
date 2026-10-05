package cl.colegiosaas.documents;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Versión publicada de un documento (DOC-03). Inmutable salvo al archivarse.
 * Nombre y RBD son una copia al momento de publicar: la evidencia no cambia si el colegio cambia.
 */
@Entity
@Table(name = "document_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InstitutionalDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StoredFile file;

    private Integer academicYear;

    private String schoolName;

    private String rbd;

    private LocalDate lastUpdatedOn;

    private boolean currentVersion;

    /** DOC-05: si el PDF no es accesible, el sitio lo advierte. */
    private boolean accessiblePdf;

    private String changeNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount publishedBy;

    DocumentVersion(InstitutionalDocument document, InstitutionalDocument.VersionDetails details,
                    String schoolName, String rbd, UserAccount publishedBy) {
        this.document = document;
        this.file = details.file();
        this.academicYear = details.academicYear();
        this.lastUpdatedOn = details.lastUpdatedOn();
        this.accessiblePdf = details.accessiblePdf();
        this.changeNotes = details.changeNotes();
        this.schoolName = schoolName;
        this.rbd = rbd;
        this.publishedBy = publishedBy;
        this.currentVersion = true;
    }

    void archive() {
        currentVersion = false;
    }
}
