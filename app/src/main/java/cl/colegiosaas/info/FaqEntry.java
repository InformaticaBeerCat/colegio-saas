package cl.colegiosaas.info;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pregunta frecuente. También alimenta el schema.org FAQPage (SEO-01) y, en premium, el chatbot. */
@Entity
@Table(name = "faq_entry")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private FaqCategory category;

    @NotBlank
    private String question;

    /** HTML simple saneado. */
    @NotBlank
    @Size(max = 4000)
    private String answer;

    private int sortOrder;

    private boolean published = true;

    public FaqEntry(FaqCategory category, String question, String answer, int sortOrder) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
    }
}
