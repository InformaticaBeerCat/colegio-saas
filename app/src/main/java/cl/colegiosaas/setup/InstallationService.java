package cl.colegiosaas.setup;

import cl.colegiosaas.admissions.AdmissionSettings;
import cl.colegiosaas.admissions.AdmissionSettingsRepository;
import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.PasswordPolicy;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.shared.persistence.SingletonEntity;
import cl.colegiosaas.site.SiteDesign;
import cl.colegiosaas.site.SiteSettings;
import cl.colegiosaas.site.SiteSettingsRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Instalación de primer arranque (CFG-01), como la de Nextcloud: crea en una sola transacción el
 * perfil del colegio, la configuración inicial del sitio y la admisión, y la cuenta del Super Admin.
 */
@Service
public class InstallationService {

    private final SchoolRepository schools;
    private final SiteSettingsRepository siteSettings;
    private final AdmissionSettingsRepository admissionSettings;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrail audit;
    private final Clock clock;

    /** Una vez instalado no se vuelve atrás: basta recordar el "sí" y no consultar la base en cada petición. */
    private final AtomicBoolean installed = new AtomicBoolean();

    InstallationService(SchoolRepository schools, SiteSettingsRepository siteSettings,
                        AdmissionSettingsRepository admissionSettings, UserAccountRepository users,
                        PasswordEncoder passwordEncoder, AuditTrail audit, Clock clock) {
        this.schools = schools;
        this.siteSettings = siteSettings;
        this.admissionSettings = admissionSettings;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    public boolean isInstalled() {
        if (installed.get()) {
            return true;
        }
        boolean done = schools.existsByIdAndSetupCompletedAtIsNotNull(SingletonEntity.ID);
        if (done) {
            installed.set(true);
        }
        return done;
    }

    @Transactional
    public UserAccount install(Installation data) {
        if (schools.existsById(SingletonEntity.ID)) {
            throw new IllegalStateException("Esta instalación ya está configurada");
        }
        List<String> passwordProblems = PasswordPolicy.problems(data.adminPassword(), data.adminEmail());
        if (!passwordProblems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", passwordProblems));
        }

        School school = new School(data.schoolName().strip(), data.plan());
        school.setRbd(blankToNull(data.rbd()));
        school.setDependency(data.dependency());
        school.setContactEmail(blankToNull(data.contactEmail()));
        school.completeSetup();
        schools.save(school);

        siteSettings.save(new SiteSettings(SiteDesign.defaults()));
        // El proceso de admisión que se publica es el del año siguiente.
        admissionSettings.save(AdmissionSettings.defaultFor(data.dependency(), Year.now(clock).getValue() + 1));

        UserAccount admin = new UserAccount(data.adminEmail().strip(), data.adminName().strip(), Role.SUPER_ADMIN);
        admin.changePassword(passwordEncoder.encode(data.adminPassword()));
        admin.activate();
        users.save(admin);

        audit.recordFor(admin, AuditAction.SETUP_COMPLETED, "School", school.getId(), "Instalación inicial de " + school.getName());
        return admin;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
