package cl.colegiosaas.admin;

import cl.colegiosaas.identity.AccountService;
import cl.colegiosaas.security.SchoolUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Portada del panel. Por ahora muestra la cuenta; cada fase agrega sus accesos. */
@Controller
class DashboardController {

    private final AccountService accounts;

    DashboardController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/admin")
    String dashboard(@AuthenticationPrincipal SchoolUser me, Model model) {
        model.addAttribute("account", accounts.get(me.id()));
        return "admin/dashboard";
    }
}
