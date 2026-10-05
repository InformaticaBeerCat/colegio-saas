package cl.colegiosaas.setup;

import cl.colegiosaas.identity.PasswordPolicy;
import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.SchoolDependency;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** Asistente de primer arranque. Una vez instalado, {@code /setup} deja de existir (404). */
@Controller
@RequestMapping("/setup")
class SetupController {

    private final InstallationService installation;
    private final SetupToken setupToken;

    SetupController(InstallationService installation, SetupToken setupToken) {
        this.installation = installation;
        this.setupToken = setupToken;
    }

    @ModelAttribute
    void options(Model model) {
        model.addAttribute("dependencies", SchoolDependency.values());
        model.addAttribute("plans", Plan.values());
    }

    @GetMapping
    String form(Model model) {
        requireNotInstalled();
        model.addAttribute("form", new SetupForm());
        return "setup/wizard";
    }

    @PostMapping
    String install(@Valid @ModelAttribute("form") SetupForm form, BindingResult errors) {
        requireNotInstalled();
        if (!setupToken.matches(form.getToken())) {
            errors.rejectValue("token", "invalid", "El token no coincide con el del log del servidor");
        }
        if (!Objects.equals(form.getPassword(), form.getPasswordConfirmation())) {
            errors.rejectValue("passwordConfirmation", "mismatch", "Las contraseñas no coinciden");
        }
        PasswordPolicy.problems(form.getPassword(), form.getAdminEmail())
                .forEach(problem -> errors.rejectValue("password", "weak", problem));
        if (errors.hasErrors()) {
            return "setup/wizard";
        }
        installation.install(form.toInstallation());
        return "redirect:/admin/login?installed";
    }

    private void requireNotInstalled() {
        if (installation.isInstalled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
