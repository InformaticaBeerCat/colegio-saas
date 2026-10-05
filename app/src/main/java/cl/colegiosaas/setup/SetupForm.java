package cl.colegiosaas.setup;

import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.SchoolDependency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Formulario del asistente. Clase con getters/setters porque Thymeleaf enlaza los campos por propiedades. */
@Getter
@Setter
public class SetupForm {

    @NotBlank(message = "Ingresa el token que aparece en el log del servidor")
    private String token;

    @NotBlank(message = "Ingresa el nombre del colegio")
    @Size(max = 150)
    private String schoolName;

    @Pattern(regexp = "|\\d{1,5}-[\\dK]", message = "Formato de RBD: números, guion y dígito verificador (p. ej. 8485-1)")
    private String rbd;

    @NotNull(message = "Elige la dependencia")
    private SchoolDependency dependency;

    @NotNull
    private Plan plan = Plan.BASE;

    @Email(message = "Correo no válido")
    private String contactEmail;

    @NotBlank(message = "Ingresa tu nombre")
    @Size(max = 150)
    private String adminName;

    @NotBlank(message = "Ingresa tu correo")
    @Email(message = "Correo no válido")
    private String adminEmail;

    @NotBlank(message = "Ingresa una contraseña")
    private String password;

    private String passwordConfirmation;

    Installation toInstallation() {
        return new Installation(schoolName, rbd, dependency, plan, contactEmail, adminName, adminEmail, password);
    }
}
