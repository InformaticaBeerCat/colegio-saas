package cl.colegiosaas.identity.web;

import cl.colegiosaas.identity.AccountException;
import cl.colegiosaas.identity.AccountService;
import cl.colegiosaas.security.SchoolUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** "Mi cuenta": datos propios y cambio de contraseña. */
@Controller
class ProfileController {

    private final AccountService accounts;

    ProfileController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/admin/profile")
    String profile(@AuthenticationPrincipal SchoolUser me, Model model) {
        model.addAttribute("account", accounts.get(me.id()));
        return "admin/profile";
    }

    @PostMapping("/admin/profile/password")
    String changePassword(@AuthenticationPrincipal SchoolUser me, @ModelAttribute PasswordForm form,
                          HttpSession session, RedirectAttributes redirect) {
        try {
            if (!form.confirmed()) {
                throw new AccountException("Las contraseñas nuevas no coinciden");
            }
            accounts.changePassword(me.id(), form.getCurrentPassword(), form.getPassword(), session.getId());
            redirect.addFlashAttribute("notice", "Contraseña actualizada. Cerramos tus otras sesiones abiertas.");
        } catch (AccountException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/profile";
    }
}
