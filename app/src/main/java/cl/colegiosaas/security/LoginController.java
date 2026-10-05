package cl.colegiosaas.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LoginController {

    /** Los mensajes (error, sesión expirada, demasiados intentos…) llegan como parámetros y los muestra la vista. */
    @GetMapping("/admin/login")
    String login(Authentication authentication) {
        boolean fullyLoggedIn = authentication != null
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !(authentication instanceof MfaPendingAuthentication);
        return fullyLoggedIn ? "redirect:/admin" : "auth/login";
    }
}
