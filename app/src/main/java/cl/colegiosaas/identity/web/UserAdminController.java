package cl.colegiosaas.identity.web;

import cl.colegiosaas.identity.AccountException;
import cl.colegiosaas.identity.AccountService;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.security.SchoolUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Gestión de usuarios del panel (USR-01, USR-04). Solo para quien tiene el permiso USERS. */
@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('USERS')")
class UserAdminController {

    private final AccountService accounts;

    UserAdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("users", accounts.listAll());
        return "admin/users/list";
    }

    @GetMapping("/invite")
    String inviteForm(@AuthenticationPrincipal SchoolUser me, Model model) {
        model.addAttribute("form", new InviteForm());
        model.addAttribute("assignableRoles", accounts.assignableRoles(me.id()));
        return "admin/users/invite";
    }

    @PostMapping("/invite")
    String invite(@AuthenticationPrincipal SchoolUser me, @Valid @ModelAttribute("form") InviteForm form,
                  BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                accounts.invite(form.getEmail(), form.getName(), form.getRoles(), me.id());
                redirect.addFlashAttribute("notice", "Invitación enviada a " + form.getEmail());
                return "redirect:/admin/users";
            } catch (AccountException e) {
                errors.reject("account", e.getMessage());
            }
        }
        model.addAttribute("assignableRoles", accounts.assignableRoles(me.id()));
        return "admin/users/invite";
    }

    @GetMapping("/{id}")
    String edit(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, Model model) {
        UserAccount user = accounts.get(id);
        RolesForm form = new RolesForm();
        form.getRoles().addAll(user.getRoles());
        model.addAttribute("user", user);
        model.addAttribute("form", form);
        model.addAttribute("assignableRoles", accounts.assignableRoles(me.id()));
        model.addAttribute("isSelf", me.id() == id);
        return "admin/users/edit";
    }

    @PostMapping("/{id}/roles")
    String changeRoles(@AuthenticationPrincipal SchoolUser me, @PathVariable long id,
                       @ModelAttribute RolesForm form, RedirectAttributes redirect) {
        return run(redirect, id, "Roles actualizados", () -> accounts.changeRoles(id, form.getRoles(), me.id()));
    }

    @PostMapping("/{id}/deactivate")
    String deactivate(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Cuenta dada de baja y sesiones cerradas", () -> accounts.deactivate(id, me.id()));
    }

    @PostMapping("/{id}/reset-mfa")
    String resetMfa(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Segundo factor reiniciado: lo configurará en su próximo ingreso",
                () -> accounts.resetMfa(id, me.id()));
    }

    @PostMapping("/{id}/resend-invitation")
    String resendInvitation(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Invitación reenviada", () -> accounts.resendInvitation(id, me.id()));
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (AccountException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }
}
