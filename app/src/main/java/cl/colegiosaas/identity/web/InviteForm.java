package cl.colegiosaas.identity.web;

import cl.colegiosaas.identity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
public class InviteForm {

    @NotBlank(message = "Ingresa el nombre")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Ingresa el correo")
    @Email(message = "Correo no válido")
    private String email;

    @NotEmpty(message = "Elige al menos un rol")
    private Set<Role> roles = EnumSet.noneOf(Role.class);
}
