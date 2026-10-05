package cl.colegiosaas.contact;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Nota interna sobre una consulta; la familia no la ve. Se crea con {@link Inquiry#addNote}. */
@Entity
@Table(name = "inquiry_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Inquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount author;

    /** Columna LONGTEXT. */
    @Convert(converter = EncryptedStringConverter.class)
    private String body;

    InquiryNote(Inquiry inquiry, UserAccount author, String body) {
        this.inquiry = inquiry;
        this.author = author;
        this.body = body;
    }
}
