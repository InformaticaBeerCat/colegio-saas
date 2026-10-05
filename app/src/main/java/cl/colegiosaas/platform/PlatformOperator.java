package cl.colegiosaas.platform;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;

/**
 * Super Admin del proveedor. Vive fuera de los colegios: no es un {@code UserAccount}
 * de ningún tenant, así un colegio nunca puede ver ni editar a un operador.
 */
@Entity
@Table(name = "platform_operator")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformOperator extends BaseEntity {

    @Email
    @NotBlank
    @Setter(AccessLevel.NONE)
    private String email;

    @NotBlank
    private String name;

    private String passwordHash;

    private boolean active = true;

    private Instant lastLoginAt;

    public PlatformOperator(String email, String name) {
        this.email = email.toLowerCase(Locale.ROOT);
        this.name = name;
    }
}
