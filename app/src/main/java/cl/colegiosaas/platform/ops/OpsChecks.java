package cl.colegiosaas.platform.ops;

import cl.colegiosaas.media.FileScanner;
import cl.colegiosaas.platform.PlatformProperties;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.platform.license.LicenseService;
import cl.colegiosaas.shared.storage.FileStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Revisiones de salud de la instalación (OPS-06): las usan la página de estado del proveedor, el endpoint de salud
 * para el monitoreo externo y las alertas por correo.
 */
@Component
public class OpsChecks {

    public enum Level { OK, WARNING, PROBLEM }

    /** Resultado de una revisión. */
    public record Check(String key, String name, Level level, String detail) {

        public boolean failing() {
            return level != Level.OK;
        }
    }

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final FileScanner scanner;
    private final LicenseService licenses;
    private final PlatformProperties properties;
    private final SchoolTime time;
    private final Clock clock;

    OpsChecks(JdbcTemplate jdbc, FileStorage storage, FileScanner scanner, LicenseService licenses,
              PlatformProperties properties, SchoolTime time, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.scanner = scanner;
        this.licenses = licenses;
        this.properties = properties;
        this.time = time;
        this.clock = clock;
    }

    public List<Check> all() {
        List<Check> checks = new ArrayList<>();
        checks.add(database());
        checks.add(storage());
        checks.add(antivirus());
        checks.add(backups());
        checks.add(license());
        checks.add(disk());
        return checks;
    }

    Check database() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return new Check("database", "Base de datos", Level.OK, "Responde");
        } catch (RuntimeException e) {
            return new Check("database", "Base de datos", Level.PROBLEM, "No responde: " + e.getMessage());
        }
    }

    Check storage() {
        try {
            storage.exists("salud/marcador");
            return new Check("storage", "Almacenamiento de archivos", Level.OK, "Responde");
        } catch (RuntimeException e) {
            return new Check("storage", "Almacenamiento de archivos", Level.PROBLEM, "No responde: " + e.getMessage());
        }
    }

    Check antivirus() {
        if (!scanner.isActive()) {
            return new Check("antivirus", "Antivirus", properties.isProduction() ? Level.WARNING : Level.OK, "No configurado");
        }
        try {
            scanner.scan(new byte[0]);
            return new Check("antivirus", "Antivirus", Level.OK, "Responde");
        } catch (RuntimeException e) {
            return new Check("antivirus", "Antivirus", Level.PROBLEM, "No responde: las subidas de archivos fallarán");
        }
    }

    Check backups() {
        if (properties.backupStatusFile().isBlank()) {
            return new Check("backups", "Respaldos", properties.isProduction() ? Level.WARNING : Level.OK,
                    "Sin archivo de estado configurado");
        }
        Path path = Path.of(properties.backupStatusFile());
        if (!Files.exists(path)) {
            return new Check("backups", "Respaldos", Level.PROBLEM, "Todavía no hay ningún respaldo registrado");
        }
        BackupStatus status = BackupStatus.read(path);
        if (status.finishedAt() == null) {
            return new Check("backups", "Respaldos", Level.PROBLEM, status.error());
        }
        Duration age = Duration.between(status.finishedAt(), clock.instant());
        if (!status.ok()) {
            return new Check("backups", "Respaldos", Level.PROBLEM, "El último respaldo falló"
                    + (status.error() == null ? "" : ": " + status.error()));
        }
        if (age.compareTo(properties.backupMaxAge()) > 0) {
            return new Check("backups", "Respaldos", Level.PROBLEM, "El último respaldo exitoso es de hace "
                    + age.toHours() + " horas");
        }
        return new Check("backups", "Respaldos", Level.OK, "Último respaldo hace " + age.toHours() + " h ("
                + Math.max(1, status.sizeBytes() / (1024 * 1024)) + " MB)");
    }

    Check license() {
        LicenseService.Status status = licenses.status();
        return switch (status.state()) {
            case VALID -> {
                long days = ChronoUnit.DAYS.between(time.today(), status.license().expiresOn());
                yield days <= 30
                        ? new Check("license", "Licencia", Level.WARNING, "Vence en " + days + " días (" + status.license().expiresOn() + ")")
                        : new Check("license", "Licencia", Level.OK, "Vigente hasta el " + status.license().expiresOn());
            }
            case NONE -> new Check("license", "Licencia", properties.isProduction() ? Level.PROBLEM : Level.OK,
                    properties.isProduction() ? status.problem() : "Sin licencia (desarrollo: sin límites)");
            case IN_GRACE -> new Check("license", "Licencia", Level.WARNING, status.problem());
            case EXPIRED, INVALID -> new Check("license", "Licencia", Level.PROBLEM, status.problem());
        };
    }

    Check disk() {
        File root = new File(".").getAbsoluteFile();
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        if (total == 0) {
            return new Check("disk", "Espacio en disco", Level.OK, "Sin datos");
        }
        int percent = (int) (free * 100 / total);
        return new Check("disk", "Espacio en disco", percent < 10 ? Level.PROBLEM : percent < 20 ? Level.WARNING : Level.OK,
                percent + " % libre (" + free / (1024 * 1024 * 1024) + " GB)");
    }
}
