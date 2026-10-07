package cl.colegiosaas.platform.ops;

import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.PlatformProperties;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.platform.license.LicenseService;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Estado de la instalación para el proveedor (OPS-03, OPS-05, OPS-06): versión, licencia y módulos, revisiones de
 * salud y configuración de producción. La exportación completa (OPS-10) también la puede descargar el colegio.
 */
@Controller
class PlatformController {

    private final LicenseService licenses;
    private final UpdateChecker updates;
    private final OpsChecks checks;
    private final ProductionReadiness readiness;
    private final SchoolExport export;
    private final SchoolRepository schools;
    private final PlatformProperties properties;
    private final SchoolTime time;

    PlatformController(LicenseService licenses, UpdateChecker updates, OpsChecks checks, ProductionReadiness readiness,
                       SchoolExport export, SchoolRepository schools, PlatformProperties properties, SchoolTime time) {
        this.licenses = licenses;
        this.updates = updates;
        this.checks = checks;
        this.readiness = readiness;
        this.export = export;
        this.schools = schools;
        this.properties = properties;
        this.time = time;
    }

    /** Un módulo con su estado: activo y si la licencia lo permite. */
    record ModuleRow(Feature feature, boolean active, boolean allowed) {
    }

    @GetMapping("/admin/platform")
    @PreAuthorize("hasAuthority('PLATFORM')")
    String status(Model model) {
        Set<Feature> allowed = licenses.allowedFeatures();
        var school = schools.findSingleton().orElseThrow();
        List<ModuleRow> modules = Arrays.stream(Feature.values())
                .map(f -> new ModuleRow(f, school.hasFeature(f), allowed.contains(f))).toList();
        model.addAttribute("version", updates.currentVersion());
        model.addAttribute("update", updates.available().orElse(null));
        model.addAttribute("production", properties.isProduction());
        model.addAttribute("license", licenses.status());
        model.addAttribute("plan", school.getPlan());
        model.addAttribute("modules", modules);
        model.addAttribute("checks", checks.all());
        model.addAttribute("problems", readiness.problems());
        model.addAttribute("warnings", readiness.warnings());
        return "admin/platform";
    }

    @PostMapping("/admin/platform/modules")
    @PreAuthorize("hasAuthority('PLATFORM')")
    String toggle(@RequestParam Feature feature, @RequestParam boolean on, RedirectAttributes redirect) {
        try {
            licenses.setFeature(feature, on);
            redirect.addFlashAttribute("notice", on ? "Módulo activado" : "Módulo desactivado");
        } catch (RuleViolation e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/platform#modulos";
    }

    /** ZIP con todos los datos y archivos del colegio; queda en la auditoría. */
    @GetMapping("/admin/platform/export")
    @PreAuthorize("hasAnyAuthority('PLATFORM', 'SCHOOL_SETTINGS')")
    ResponseEntity<StreamingResponseBody> export() {
        String name = "colegio-" + time.today() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(export::write);
    }
}
