package cl.colegiosaas.identity.web;

import cl.colegiosaas.identity.AccountException;
import cl.colegiosaas.identity.AccountService;
import cl.colegiosaas.identity.AccountTokenPurpose;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Páginas sin sesión: aceptar una invitación y recuperar la contraseña. */
@Controller
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    // --- Invitación ---

    @GetMapping("/admin/invitation/{token}")
    String invitation(@PathVariable String token, Model model) {
        if (!accounts.isLinkValid(token, AccountTokenPurpose.INVITATION)) {
            return invalidLink(model);
        }
        return passwordPage(model, "Activa tu cuenta", "/admin/invitation/" + token);
    }

    @PostMapping("/admin/invitation/{token}")
    String acceptInvitation(@PathVariable String token, @ModelAttribute PasswordForm form, Model model) {
        if (!form.confirmed()) {
            return passwordPage(model, "Activa tu cuenta", "/admin/invitation/" + token, "Las contraseñas no coinciden");
        }
        try {
            accounts.acceptInvitation(token, form.getPassword());
            return "redirect:/admin/login?activated";
        } catch (AccountException e) {
            return passwordPage(model, "Activa tu cuenta", "/admin/invitation/" + token, e.getMessage());
        }
    }

    // --- Recuperar contraseña ---

    @GetMapping("/admin/password/forgot")
    String forgotForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/admin/password/forgot")
    String forgot(@RequestParam String email, Model model) {
        accounts.requestPasswordReset(email);
        model.addAttribute("title", "Revisa tu correo");
        model.addAttribute("message", "Si el correo corresponde a una cuenta activa, te enviamos un enlace para "
                + "restablecer la contraseña. Vence en una hora.");
        return "auth/notice";
    }

    @GetMapping("/admin/password/reset/{token}")
    String resetForm(@PathVariable String token, Model model) {
        if (!accounts.isLinkValid(token, AccountTokenPurpose.PASSWORD_RESET)) {
            return invalidLink(model);
        }
        return passwordPage(model, "Nueva contraseña", "/admin/password/reset/" + token);
    }

    @PostMapping("/admin/password/reset/{token}")
    String reset(@PathVariable String token, @ModelAttribute PasswordForm form, Model model) {
        if (!form.confirmed()) {
            return passwordPage(model, "Nueva contraseña", "/admin/password/reset/" + token, "Las contraseñas no coinciden");
        }
        try {
            accounts.resetPassword(token, form.getPassword());
            return "redirect:/admin/login?reset";
        } catch (AccountException e) {
            return passwordPage(model, "Nueva contraseña", "/admin/password/reset/" + token, e.getMessage());
        }
    }

    private static String passwordPage(Model model, String title, String action, String... problem) {
        model.addAttribute("title", title);
        model.addAttribute("action", action);
        if (problem.length > 0) {
            model.addAttribute("problem", problem[0]);
        }
        return "auth/set-password";
    }

    private static String invalidLink(Model model) {
        model.addAttribute("title", "Enlace no válido");
        model.addAttribute("message", "El enlace venció o ya se usó. Pide uno nuevo a un administrador "
                + "o desde \"Olvidé mi contraseña\".");
        return "auth/notice";
    }
}
