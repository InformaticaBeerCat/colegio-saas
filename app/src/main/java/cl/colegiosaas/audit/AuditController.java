package cl.colegiosaas.audit;

import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Consulta del registro de auditoría (USR-03), lo más reciente primero. */
@Controller
@PreAuthorize("hasAuthority('AUDIT_LOG')")
class AuditController {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final AuditLogRepository entries;
    private final SchoolRepository schools;

    AuditController(AuditLogRepository entries, SchoolRepository schools) {
        this.entries = entries;
        this.schools = schools;
    }

    @GetMapping("/admin/audit")
    String list(@RequestParam(defaultValue = "0") int page, Model model) {
        ZoneId zone = ZoneId.of(schools.findSingleton().map(School::getTimeZone).orElse("America/Santiago"));
        Page<AuditLogEntry> result = entries.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(Math.max(page, 0), 50));
        model.addAttribute("rows", result.map(entry -> new Row(
                FORMAT.format(entry.getOccurredAt().atZone(zone)),
                entry.getActorName() != null ? entry.getActorName() : entry.getActorType().name(),
                entry.getAction(),
                entry.getEntityType() == null ? "" : entry.getEntityType() + (entry.getEntityId() == null ? "" : " #" + entry.getEntityId()),
                entry.getDetails(),
                entry.getIpAddress())));
        return "admin/audit";
    }

    record Row(String when, String actor, AuditAction action, String target, String details, String ip) {
    }
}
