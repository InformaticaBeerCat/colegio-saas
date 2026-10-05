package cl.colegiosaas.admin;

import cl.colegiosaas.identity.AccountService;
import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.setup.StarterContent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Portada del panel. Por ahora muestra la cuenta; cada fase agrega sus accesos. */
@Controller
class DashboardController {

    private final AccountService accounts;
    private final StarterContent starter;

    DashboardController(AccountService accounts, StarterContent starter) {
        this.accounts = accounts;
        this.starter = starter;
    }

    @GetMapping("/admin")
    String dashboard(@AuthenticationPrincipal SchoolUser me, Model model) {
        model.addAttribute("account", accounts.get(me.id()));
        // El asistente de marca se ofrece mientras el sitio no tenga portada (CFG-01).
        model.addAttribute("showWelcome", me.can(Permission.SITE_DESIGN) && me.can(Permission.PAGES) && !starter.exists());
        return "admin/dashboard";
    }
}
