package cl.colegiosaas.identity;

import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.shared.security.SecureTokens;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * Enlace de un solo uso enviado por correo: aceptar invitación o restablecer contraseña.
 * Se guarda solo el hash del token; el token viaja únicamente en el correo.
 */
@Entity
@Table(name = "account_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id")
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    private AccountTokenPurpose purpose;

    private String tokenHash;

    private Instant expiresAt;

    private Instant usedAt;

    /** Token recién emitido: la entidad a guardar y el valor para el enlace del correo. */
    public record Issued(AccountToken token, String rawToken) {
    }

    public static Issued issue(UserAccount user, AccountTokenPurpose purpose, Instant now) {
        String raw = SecureTokens.newToken();
        AccountToken token = new AccountToken();
        token.user = Objects.requireNonNull(user, "user");
        token.purpose = purpose;
        token.tokenHash = SecureTokens.hash(raw);
        token.expiresAt = now.plus(purpose.validity());
        return new Issued(token, raw);
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant at) {
        usedAt = at;
    }
}
