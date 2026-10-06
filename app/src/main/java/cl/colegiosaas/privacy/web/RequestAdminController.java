package cl.colegiosaas.privacy.web;

import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.DataSubjectRequestService;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Bandeja de solicitudes de derechos (PRV-05): lo que vence primero, arriba. */
@Controller
@RequestMapping("/admin/privacy/requests")
@PreAuthorize("hasAuthority('PRIVACY')")
class RequestAdminController {

    private final DataSubjectRequestService requests;
    private final SchoolTime time;

    RequestAdminController(DataSubjectRequestService requests, SchoolTime time) {
        this.requests = requests;
        this.time = time;
    }

    @GetMapping
    String inbox(Model model) {
        model.addAttribute("open", requests.open());
        model.addAttribute("closed", requests.recentlyClosed());
        model.addAttribute("today", time.today());
        return "admin/privacy/requests";
    }

    @GetMapping("/{id}")
    String show(@PathVariable long id, Model model) {
        model.addAttribute("request", requests.view(id));
        model.addAttribute("today", time.today());
        return "admin/privacy/request";
    }

    @PostMapping("/{id}/identity")
    String identity(@PathVariable long id, @RequestParam String message, RedirectAttributes redirect) {
        return run(redirect, id, "Se pidió acreditar identidad por correo", () -> requests.requestIdentity(id, message));
    }

    @PostMapping("/{id}/start")
    String start(@AuthenticationPrincipal SchoolUser me, @PathVariable long id, RedirectAttributes redirect) {
        return run(redirect, id, "Solicitud en proceso, a tu cargo", () -> requests.start(id, me.id()));
    }

    @PostMapping("/{id}/complete")
    String complete(@PathVariable long id, @RequestParam String resolution, RedirectAttributes redirect) {
        return run(redirect, id, "Solicitud respondida; la persona recibió la respuesta por correo",
                () -> requests.complete(id, resolution));
    }

    @PostMapping("/{id}/reject")
    String reject(@PathVariable long id, @RequestParam String reason, RedirectAttributes redirect) {
        return run(redirect, id, "Solicitud rechazada; la persona recibió la fundamentación por correo",
                () -> requests.reject(id, reason));
    }

    private static String run(RedirectAttributes redirect, long id, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", success);
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/privacy/requests/" + id;
    }
}
