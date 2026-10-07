package cl.colegiosaas.web;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditLogRepository;
import cl.colegiosaas.contact.ContactArea;
import cl.colegiosaas.contact.ContactAreaRepository;
import cl.colegiosaas.contact.Inquiry;
import cl.colegiosaas.contact.InquiryRepository;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.SchoolDependency;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.license.License;
import cl.colegiosaas.platform.license.LicenseCodec;
import cl.colegiosaas.platform.license.LicenseService;
import cl.colegiosaas.platform.ops.OpsAlerts;
import cl.colegiosaas.platform.ops.SchoolExport;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.ConsentService;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.setup.Installation;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.storage.FileStorage;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Licencia (OPS-05), salud y alertas (OPS-06, SEG-05) y exportación completa (OPS-10) en una instalación con licencia. */
class OperationsTest extends WebTestSupport {

    static final KeyPair KEYS = generate();
    static final Path BACKUP_STATUS = Path.of("target/test-files/last-backup.json");

    @DynamicPropertySource
    static void license(DynamicPropertyRegistry registry) {
        License license = new License("lic-ops", "Colegio San José", "localhost", Plan.COMMUNITY, Set.of(Feature.WHATSAPP_SMS),
                LocalDate.of(2026, 1, 1), LocalDate.of(2099, 12, 31));
        registry.add("app.platform.license-public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("app.platform.license", () -> LicenseCodec.sign(license, KEYS.getPrivate()));
        registry.add("app.platform.alerts-email", () -> "soporte@proveedor.cl");
        registry.add("app.platform.backup-status-file", BACKUP_STATUS::toString);
    }

    @Autowired LicenseService licenses;
    @Autowired SchoolRepository schools;
    @Autowired OpsAlerts alerts;
    @Autowired AuditLogRepository auditLog;
    @Autowired LegalTextService legalTexts;
    @Autowired ConsentService consents;
    @Autowired ContactAreaRepository areas;
    @Autowired InquiryRepository inquiries;
    @Autowired FileStorage storage;
    @Autowired BlindIndex index;

    UserAccount provider;

    @BeforeEach
    void setUp() throws Exception {
        // El asistente pide el plan Base, pero la licencia manda: Comunidad.
        provider = installation.install(new Installation("Colegio San José", "8485-1", SchoolDependency.PRIVATE_SUBSIDIZED,
                Plan.BASE, "contacto@colegio.cl", "Proveedor", "super@colegio.cl", PASSWORD));
        Files.createDirectories(BACKUP_STATUS.getParent());
        Files.deleteIfExists(BACKUP_STATUS);
    }

    @AfterEach
    void cleanUp() throws Exception {
        Files.deleteIfExists(BACKUP_STATUS);
    }

    @Test
    void theLicenseSetsThePlanAndLimitsTheModules() throws Exception {
        assertThat(schools.findSingleton().orElseThrow().getPlan()).isEqualTo(Plan.COMMUNITY);
        assertThat(licenses.status().state()).isEqualTo(LicenseService.State.VALID);

        // Un módulo fuera de la licencia se apaga al revisarla.
        schools.findSingleton().orElseThrow().enableFeature(Feature.OWN_ADMISSIONS);
        licenses.enforce();
        assertThat(schools.findSingleton().orElseThrow().hasFeature(Feature.OWN_ADMISSIONS)).isFalse();

        mvc.perform(get("/admin/platform").with(as(provider)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Colegio San José")))
                .andExpect(content().string(containsString("WHATSAPP_SMS")))
                .andExpect(content().string(containsString("No incluido")));
        mvc.perform(post("/admin/platform/modules").param("feature", "WHATSAPP_SMS").param("on", "true")
                        .with(as(provider)).with(csrf()))
                .andExpect(flash().attribute("notice", "Módulo activado"));
        mvc.perform(post("/admin/platform/modules").param("feature", "PAYMENTS").param("on", "true")
                        .with(as(provider)).with(csrf()))
                .andExpect(flash().attribute("problem", "La licencia no incluye ese módulo"));
        assertThat(schools.findSingleton().orElseThrow().hasFeature(Feature.WHATSAPP_SMS)).isTrue();

        UserAccount admin = activeUser("directora@colegio.cl", Role.SCHOOL_ADMIN);
        mvc.perform(get("/admin/platform").with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    void healthShowsDetailsOnlyToTheProviderAndLivenessIgnoresBackups() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        // Sin respaldo registrado, el estado general está caído (para el monitoreo externo).
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health").with(as(provider)))
                .andExpect(jsonPath("$.components.backups.status").value("DOWN"))
                .andExpect(jsonPath("$.components.license.status").value("UP"));

        writeBackupStatus(Instant.now().minus(2, ChronoUnit.HOURS), true);
        mvc.perform(get("/actuator/health").with(as(provider)))
                .andExpect(jsonPath("$.components.backups.status").value("UP"));
    }

    @Test
    void problemsAreEmailedOncePerDay() throws Exception {
        writeBackupStatus(Instant.now().minus(3, ChronoUnit.DAYS), true);

        assertThat(alerts.run()).anySatisfy(m -> assertThat(m).contains("Respaldos", "hace 72 horas"));
        assertThat(mails()).extracting(OutgoingMail::to).contains("soporte@proveedor.cl", "super@colegio.cl");
        assertThat(alerts.run()).isEmpty();

        // Un problema distinto sí se avisa.
        writeBackupStatus(Instant.now(), false);
        assertThat(alerts.run()).anySatisfy(m -> assertThat(m).contains("El último respaldo falló"));
    }

    @Test
    void theExportHasReadableDataAndFilesButNoAccessSecrets() throws Exception {
        legalTexts.publishFromTemplate(LegalTextKind.NOTICE_CONTACT, provider.getId());
        ContactArea area = areas.findAll().getFirst();
        inquiries.save(new Inquiry(area, new DataSubject("Rosa Muñoz", "rosa@correo.cl"), null, "Matrícula", "¿Hay vacantes?",
                consents.record(new DataSubject("Rosa Muñoz", "rosa@correo.cl"), ConsentPurpose.CONTACT, true,
                        new RequestOrigin("/contacto", "127.0.0.1", "JUnit")), index));
        storage.put("pruebas/exportacion.pdf", pdf("documento"));
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("insert into stored_file (version, created_at, updated_at, storage_key, original_name, content_type, size_bytes, sha256, variants, scan_status)"
                + " values (0, current_timestamp, current_timestamp, 'pruebas/exportacion.pdf', 'exportacion.pdf', 'application/pdf', 10, ?, '[]', 'CLEAN')",
                "%064d".formatted(7));

        // La descarga corre en streaming en otro hilo, que no ve la transacción del test: el contenido se revisa
        // generándolo aquí, y el endpoint por sus permisos y cabeceras.
        mvc.perform(get("/admin/platform/export").with(as(provider)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"colegio-")))
                .andExpect(header().string("Cache-Control", "no-store"));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        export.write(out);
        byte[] zip = out.toByteArray();
        Map<String, String> entries = unzip(zip);

        assertThat(entries).containsKeys("LEEME.txt", "datos/school.json", "datos/user_account.json", "datos/inquiry.json",
                "archivos/pruebas/exportacion.pdf");
        assertThat(entries).doesNotContainKeys("datos/account_token.json", "datos/flyway_schema_history.json");
        assertThat(entries.get("datos/inquiry.json")).contains("\"name\":\"Rosa Muñoz\"", "¿Hay vacantes?").doesNotContain("\"v1:");
        assertThat(entries.get("datos/user_account.json")).contains("super@colegio.cl").doesNotContain("password_hash", "{bcrypt}");
        assertThat(entries.get("archivos/pruebas/exportacion.pdf")).startsWith("%PDF-");
        assertThat(auditLog.findByActionOrderByIdAsc(AuditAction.EXPORT))
                .anySatisfy(e -> assertThat(e.getDetails()).isEqualTo("Exportación completa del colegio"));

        UserAccount editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
        mvc.perform(get("/admin/platform/export").with(as(editor))).andExpect(status().isForbidden());
    }

    @Test
    void anotherPublicKeyRejectsTheLicense() throws Exception {
        String foreign = LicenseCodec.sign(new License("x", "Otro", "localhost", Plan.ADMISSIONS_PRO, Set.of(), LocalDate.of(2026, 1, 1),
                LocalDate.of(2099, 1, 1)), generate().getPrivate());
        assertThatThrownBy(() -> LicenseCodec.verify(foreign, KEYS.getPublic())).isInstanceOf(LicenseCodec.InvalidLicense.class);
    }

    @Autowired javax.sql.DataSource dataSource;
    @Autowired SchoolExport export;

    private void writeBackupStatus(Instant finishedAt, boolean ok) throws Exception {
        Files.writeString(BACKUP_STATUS, """
                {"finishedAt": "%s", "ok": %s, "sizeBytes": 52428800, "file": "colegio-2026-10-07.tar.gz"}
                """.formatted(finishedAt, ok));
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
