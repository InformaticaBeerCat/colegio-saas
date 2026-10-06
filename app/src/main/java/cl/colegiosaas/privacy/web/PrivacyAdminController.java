package cl.colegiosaas.privacy.web;

import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.DataSubjectRequestService;
import cl.colegiosaas.privacy.IncidentService;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.privacy.PersonalDataService;
import cl.colegiosaas.privacy.RetentionAction;
import cl.colegiosaas.privacy.RetentionCategory;
import cl.colegiosaas.privacy.RetentionService;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Portada de privacidad del panel, búsqueda de una persona (PRV-07) y plazos de conservación (PRV-06). */
@Controller
@RequestMapping("/admin/privacy")
@PreAuthorize("hasAuthority('PRIVACY')")
class PrivacyAdminController {

    /** Textos sin los cuales el sitio no cumple: la política y el aviso del formulario de derechos. */
    static final List<LegalTextKind> REQUIRED = List.of(LegalTextKind.PRIVACY_POLICY, LegalTextKind.NOTICE_DATA_REQUESTS);

    private final LegalTextService legalTexts;
    private final DataSubjectRequestService requests;
    private final IncidentService incidents;
    private final PersonalDataService personalData;
    private final RetentionService retention;
    private final SchoolTime time;

    PrivacyAdminController(LegalTextService legalTexts, DataSubjectRequestService requests, IncidentService incidents,
                           PersonalDataService personalData, RetentionService retention, SchoolTime time) {
        this.legalTexts = legalTexts;
        this.requests = requests;
        this.incidents = incidents;
        this.personalData = personalData;
        this.retention = retention;
        this.time = time;
    }

    @GetMapping
    String hub(Model model) {
        model.addAttribute("missing", REQUIRED.stream().filter(k -> legalTexts.current(k).isEmpty()).toList());
        model.addAttribute("openRequests", requests.open().size());
        model.addAttribute("overdueRequests", requests.overdueCount());
        model.addAttribute("openIncidents", incidents.openCount());
        return "admin/privacy/hub";
    }

    /** El email va por POST: así no queda en el historial del navegador ni en los registros del servidor. */
    @GetMapping("/people")
    String people() {
        return "admin/privacy/people";
    }

    @PostMapping("/people")
    String search(@RequestParam String email, Model model) {
        model.addAttribute("email", email.strip());
        try {
            model.addAttribute("dossier", personalData.find(email));
        } catch (RuleViolation e) {
            model.addAttribute("problem", e.getMessage());
        }
        return "admin/privacy/people";
    }

    @PostMapping("/people/export")
    ResponseEntity<byte[]> export(@RequestParam String email) {
        byte[] json = personalData.exportJson(email).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("datos-personales-" + time.today() + ".json").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(json);
    }

    @PostMapping("/people/erase")
    String erase(@RequestParam String email, @RequestParam(required = false) String confirmEmail, RedirectAttributes redirect) {
        if (confirmEmail == null || !confirmEmail.strip().equalsIgnoreCase(email.strip())) {
            redirect.addFlashAttribute("problem", "Para suprimir, vuelve a escribir el email exactamente");
            return "redirect:/admin/privacy/people";
        }
        try {
            PersonalDataService.Erasure done = personalData.erase(email);
            redirect.addFlashAttribute("notice", "Datos suprimidos: %d registro(s) borrados, %d consentimiento(s) anonimizados, %d estudiante(s) sin email de apoderado."
                    .formatted(done.deleted(), done.anonymized(), done.detached()));
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/privacy/people";
    }

    @GetMapping("/retention")
    String retention(Model model) {
        model.addAttribute("policies", retention.policies());
        return "admin/privacy/retention";
    }

    @PostMapping("/retention/{id}")
    String updatePolicy(@PathVariable long id, @RequestParam int days, @RequestParam RetentionAction action,
                        RedirectAttributes redirect) {
        try {
            retention.update(id, days, action);
            redirect.addFlashAttribute("notice", "Plazo guardado");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/privacy/retention";
    }

    @PostMapping("/retention/run")
    String runRetention(RedirectAttributes redirect) {
        Map<RetentionCategory, Integer> done = retention.apply();
        int total = done.values().stream().mapToInt(Integer::intValue).sum();
        redirect.addFlashAttribute("notice", total == 0 ? "No había datos vencidos"
                : "Retención aplicada: " + Arrays.stream(RetentionCategory.values()).filter(c -> done.getOrDefault(c, 0) > 0)
                .map(c -> done.get(c) + " " + label(c)).collect(Collectors.joining(", ")));
        return "redirect:/admin/privacy/retention";
    }

    private static String label(RetentionCategory category) {
        return switch (category) {
            case INQUIRIES -> "consulta(s)";
            case PROSPECTS -> "registro(s) de admisión";
            case APPOINTMENTS -> "cita(s)";
            case EVENT_REGISTRATIONS -> "inscripción(es)";
            case DATA_SUBJECT_REQUESTS -> "solicitud(es) de derechos";
            case CONSENT_RECORDS -> "consentimiento(s)";
            case STUDENTS -> "estudiante(s)";
            case AUDIT_LOG -> "entrada(s) de auditoría";
        };
    }
}
