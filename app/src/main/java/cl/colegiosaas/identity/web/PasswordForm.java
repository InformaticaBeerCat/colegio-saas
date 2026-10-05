package cl.colegiosaas.identity.web;

import lombok.Getter;
import lombok.Setter;

/** Nueva contraseña con confirmación; {@code currentPassword} solo se usa en "Mi cuenta". */
@Getter
@Setter
public class PasswordForm {

    private String currentPassword;

    private String password;

    private String passwordConfirmation;

    boolean confirmed() {
        return password != null && password.equals(passwordConfirmation);
    }
}
