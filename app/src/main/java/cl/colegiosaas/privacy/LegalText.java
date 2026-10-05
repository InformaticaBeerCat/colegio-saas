package cl.colegiosaas.privacy;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.shared.persistence.BaseEntity;
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

import java.time.Instant;
import java.util.Objects;

/**
 * Versión de un texto legal: política de privacidad, cookies, aviso de un formulario…
 * Una vez publicada es inmutable: cada consentimiento apunta a la versión exacta que la persona
 * aceptó (PRV-04). Para cambiar el texto se crea la versión siguiente.
 */
@Entity
@Table(name = "legal_text")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalText extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private LegalTextKind kind;

    private int versionNumber;

    @NotBlank
    private String title;

    /** HTML. Columna LONGTEXT. */
    @NotBlank
    private String content;

    /** Nulo mientras es borrador. */
    private Instant effectiveFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount publishedBy;

    public LegalText(LegalTextKind kind, int versionNumber, String title, String content) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.versionNumber = versionNumber;
        this.title = title;
        this.content = content;
    }

    public void edit(String newTitle, String newContent) {
        if (isPublished()) {
            throw new IllegalStateException("Un texto legal publicado no se edita: crea la versión siguiente");
        }
        title = newTitle;
        content = newContent;
    }

    public void publish(UserAccount by) {
        if (isPublished()) {
            throw new IllegalStateException("Esta versión ya está publicada");
        }
        effectiveFrom = Instant.now();
        publishedBy = by;
    }

    public boolean isPublished() {
        return effectiveFrom != null;
    }
}
