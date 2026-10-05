package cl.colegiosaas.security;

import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

/**
 * Pantallas del segundo factor (opcional; obligatorio solo si la instalación lo exige):
 * <ul>
 *   <li>{@code /enable} y {@code /disable}: la persona lo activa o desactiva desde "Mi cuenta".</li>
 *   <li>{@code /setup} y {@code /verify}: pasos del ingreso, con la sesión a medio camino. Sus POST
 *       escriben la redirección ellos mismos: {@link LoginFlow#complete} decide adónde ir.</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/mfa")
class MfaController {

    static final String PENDING_SECRET = "mfa.pendingSecret";
    static final String RECOVERY_CODES = "mfa.recoveryCodes";

    private final MfaService mfa;
    private final LoginFlow loginFlow;
    private final LoginAttempts attempts;
    private final SchoolRepository schools;

    MfaController(MfaService mfa, LoginFlow loginFlow, LoginAttempts attempts, SchoolRepository schools) {
        this.mfa = mfa;
        this.loginFlow = loginFlow;
        this.attempts = attempts;
        this.schools = schools;
    }

    // --- Activación voluntaria desde "Mi cuenta" ---

    @GetMapping("/enable")
    String enableForm(@AuthenticationPrincipal SchoolUser me, HttpSession session, Model model) {
        if (mfa.isEnabled(me.id())) {
            return "redirect:/admin/profile";
        }
        return enrollmentPage(session, model, me.email(), "/admin/mfa/enable", true);
    }

    @PostMapping("/enable")
    String enable(@AuthenticationPrincipal SchoolUser me, @RequestParam String code, HttpSession session) {
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (secret == null) {
            return "redirect:/admin/mfa/enable";
        }
        try {
            List<String> codes = mfa.enroll(me.id(), secret, code);
            session.removeAttribute(PENDING_SECRET);
            session.setAttribute(RECOVERY_CODES, codes);
            return "redirect:/admin/mfa/recovery-codes";
        } catch (MfaService.InvalidMfaCodeException e) {
            return "redirect:/admin/mfa/enable?error";
        }
    }

    @PostMapping("/disable")
    String disable(@AuthenticationPrincipal SchoolUser me, @RequestParam(required = false) String currentPassword,
                   RedirectAttributes redirect) {
        try {
            mfa.disable(me.id(), currentPassword);
            redirect.addFlashAttribute("notice", "Verificación en dos pasos desactivada.");
        } catch (MfaService.MfaChangeRejectedException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/profile";
    }

    // --- Pasos del ingreso (con la sesión a medio camino) ---

    /** Solo llega aquí quien debe usar MFA por exigencia de la instalación y aún no lo configura. */
    @GetMapping("/setup")
    String setup(HttpSession session, Model model) {
        MfaPendingAuthentication pending = pending();
        if (pending.user().isMfaEnabled()) {
            return "redirect:/admin/mfa/verify";
        }
        return enrollmentPage(session, model, pending.user().email(), LoginFlow.MFA_SETUP_URL, false);
    }

    @PostMapping("/setup")
    void confirmSetup(@RequestParam String code, HttpSession session, HttpServletRequest request,
                      HttpServletResponse response) throws IOException, ServletException {
        MfaPendingAuthentication pending = pending();
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (secret == null) {
            response.sendRedirect(request.getContextPath() + LoginFlow.MFA_SETUP_URL);
            return;
        }
        try {
            List<String> codes = mfa.enroll(pending.user().id(), secret, code);
            session.removeAttribute(PENDING_SECRET);
            session.setAttribute(RECOVERY_CODES, codes);
            loginFlow.complete(pending.primary(), request, response, "/admin/mfa/recovery-codes");
        } catch (MfaService.InvalidMfaCodeException e) {
            response.sendRedirect(request.getContextPath() + LoginFlow.MFA_SETUP_URL + "?error");
        }
    }

    @GetMapping("/verify")
    String verifyForm() {
        return "auth/mfa-verify";
    }

    @PostMapping("/verify")
    void verify(@RequestParam String code, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        MfaPendingAuthentication pending = pending();
        long userId = pending.user().id();
        if (mfa.verify(userId, code)) {
            loginFlow.complete(pending.primary(), request, response, null);
            return;
        }
        if (attempts.recordMfaFailure(userId, request.getRemoteAddr())) {
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
            response.sendRedirect(request.getContextPath() + "/admin/login?error");
        } else {
            response.sendRedirect(request.getContextPath() + LoginFlow.MFA_VERIFY_URL + "?error");
        }
    }

    /** Se muestran una sola vez, justo después de activar el MFA. */
    @GetMapping("/recovery-codes")
    @SuppressWarnings("unchecked")
    String recoveryCodes(HttpSession session, Model model) {
        List<String> codes = (List<String>) session.getAttribute(RECOVERY_CODES);
        if (codes == null) {
            return "redirect:/admin";
        }
        session.removeAttribute(RECOVERY_CODES);
        model.addAttribute("codes", codes);
        return "auth/recovery-codes";
    }

    /** El secreto vive en la sesión hasta que la persona confirma un código: recién ahí va a la base. */
    private String enrollmentPage(HttpSession session, Model model, String email, String action, boolean voluntary) {
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (secret == null) {
            secret = Totp.newSecret();
            session.setAttribute(PENDING_SECRET, secret);
        }
        String issuer = schools.findSingleton().map(School::getName).orElse("Colegio");
        model.addAttribute("qr", QrCodes.svgDataUri(Totp.otpauthUri(issuer, email, secret)));
        model.addAttribute("secret", secret.replaceAll("(.{4})(?!$)", "$1 "));
        model.addAttribute("action", action);
        model.addAttribute("voluntary", voluntary);
        return "auth/mfa-setup";
    }

    private static MfaPendingAuthentication pending() {
        return (MfaPendingAuthentication) SecurityContextHolder.getContext().getAuthentication();
    }
}
