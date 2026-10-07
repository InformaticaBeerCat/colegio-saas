package cl.colegiosaas.admissions;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.calendar.Event;
import cl.colegiosaas.calendar.EventKind;
import cl.colegiosaas.calendar.EventRepository;
import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Features;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.platform.SchoolTime;
import cl.colegiosaas.privacy.ConsentPurpose;
import cl.colegiosaas.privacy.ConsentRecord;
import cl.colegiosaas.privacy.ConsentService;
import cl.colegiosaas.privacy.DataSubject;
import cl.colegiosaas.privacy.RequestOrigin;
import cl.colegiosaas.privacy.RetentionCategory;
import cl.colegiosaas.privacy.RetentionPolicy;
import cl.colegiosaas.privacy.RetentionPolicyRepository;
import cl.colegiosaas.scheduling.AppointmentAudience;
import cl.colegiosaas.scheduling.AppointmentType;
import cl.colegiosaas.scheduling.AppointmentTypeRepository;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.html.HtmlSanitizer;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.text.Emails;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.shared.web.SafeUrls;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admisión (ADM-01..03, ADM-07, ADM-08): la página del proceso según el modo (SAE o propio), sus hitos, vacantes
 * y edades por nivel configurables, las visitas, y el registro de interés de las familias con su consentimiento.
 */
@Service
public class AdmissionService {

    private final AdmissionSettingsRepository settings;
    private final AdmissionMilestoneRepository milestones;
    private final VacancyRepository vacancies;
    private final ProspectRepository prospects;
    private final GradeLevelRepository levels;
    private final AppointmentTypeRepository appointmentTypes;
    private final EventRepository events;
    private final RetentionPolicyRepository retention;
    private final ConsentService consents;
    private final UserAccountRepository users;
    private final SchoolRepository schools;
    private final Features features;
    private final SchoolTime time;
    private final AppProperties app;
    private final BlindIndex index;
    private final AuditTrail audit;
    private final ApplicationEventPublisher publisher;

    AdmissionService(AdmissionSettingsRepository settings, AdmissionMilestoneRepository milestones, VacancyRepository vacancies,
                     ProspectRepository prospects, GradeLevelRepository levels, AppointmentTypeRepository appointmentTypes,
                     EventRepository events, RetentionPolicyRepository retention, ConsentService consents,
                     UserAccountRepository users, SchoolRepository schools, Features features, SchoolTime time,
                     AppProperties app, BlindIndex index, AuditTrail audit, ApplicationEventPublisher publisher) {
        this.settings = settings;
        this.milestones = milestones;
        this.vacancies = vacancies;
        this.prospects = prospects;
        this.levels = levels;
        this.appointmentTypes = appointmentTypes;
        this.events = events;
        this.retention = retention;
        this.consents = consents;
        this.users = users;
        this.schools = schools;
        this.features = features;
        this.time = time;
        this.app = app;
        this.index = index;
        this.audit = audit;
        this.publisher = publisher;
    }

    // --- Página pública --------------------------------------------------------------------------------------

    /** Un nivel tal como se muestra: si recibe postulantes, sus fechas de nacimiento y sus vacantes. */
    public record LevelInfo(GradeLevel level, boolean open, LocalDate bornFrom, LocalDate bornUntil, Integer seats) {
    }

    /** Todo lo que muestra la página de admisión. */
    public record Page(AdmissionSettings settings, List<AdmissionMilestone> milestones, List<LevelInfo> levels,
                       List<AppointmentType> visits, List<Event> openHouses) {

        public List<LevelInfo> openLevels() {
            return levels.stream().filter(LevelInfo::open).toList();
        }

        public boolean showsVacancies() {
            return settings.isShowVacancies() && levels.stream().anyMatch(l -> l.seats() != null);
        }
    }

    @Transactional(readOnly = true)
    public Page page() {
        AdmissionSettings current = current();
        List<AppointmentType> visits = features.on(Feature.SCHEDULING)
                ? appointmentTypes.findWithHostsByActiveTrueOrderByNameAsc().stream()
                .filter(t -> t.getAudience() == AppointmentAudience.PROSPECTIVE_FAMILY && !t.getHosts().isEmpty()).toList()
                : List.of();
        List<Event> openHouses = features.on(Feature.CALENDAR)
                ? events.findPublishedBetween(time.now(), time.now().plusYears(1)).stream()
                .filter(e -> e.getKind() == EventKind.OPEN_HOUSE).toList()
                : List.of();
        return new Page(current, milestones.findByProcessYearOrderByStartsOnAscSortOrderAsc(current.getProcessYear()),
                levelInfo(current), visits, openHouses);
    }

    // --- Registro de interés ---------------------------------------------------------------------------------

    /**
     * Lo que llega del formulario. El consentimiento para registrar el interés y el de recibir correos de
     * seguimiento son casillas separadas (PRV-02, ADM-06): la segunda es opcional.
     */
    public record Interest(String name, String email, String phone, Long gradeLevelId, boolean consent, boolean followUp,
                           String utmSource, String utmCampaign) {
    }

    @Transactional
    public Prospect registerInterest(Interest form, RequestOrigin origin) {
        AdmissionSettings current = current();
        String name = required(form.name(), 150, "Escribe tu nombre (hasta 150 caracteres)");
        String email = form.email() == null ? "" : form.email().strip();
        if (!Emails.isValid(email)) {
            throw new RuleViolation("Escribe un correo válido: ahí te enviaremos la información");
        }
        GradeLevel level = null;
        if (form.gradeLevelId() != null) {
            level = levelInfo(current).stream().filter(l -> l.open() && l.level().getId().equals(form.gradeLevelId()))
                    .map(LevelInfo::level).findFirst()
                    .orElseThrow(() -> new RuleViolation("Ese nivel no recibe postulantes este año"));
        }
        if (!form.consent()) {
            throw new RuleViolation("Para registrar tu interés necesitamos tu autorización para usar estos datos: marca la casilla");
        }
        DataSubject guardian = new DataSubject(name, email);
        ConsentRecord consent = consents.record(guardian, ConsentPurpose.ADMISSIONS, true, origin);
        // El "no" del seguimiento también se registra: prueba de que la casilla se ofreció sin marcar.
        ConsentRecord followUp = consents.record(guardian, ConsentPurpose.ADMISSIONS_FOLLOW_UP, form.followUp(), origin);
        int days = retention.findByDataCategory(RetentionCategory.PROSPECTS).map(RetentionPolicy::getRetentionDays).orElse(365);
        Prospect prospect = new Prospect(guardian, optional(form.phone(), 30), level, current.getProcessYear(),
                ProspectSource.WEBSITE_FORM, consent, time.today().plusDays(days), index);
        prospect.setFollowUpConsent(followUp);
        prospect.setUtmSource(optional(form.utmSource(), 100));
        prospect.setUtmCampaign(optional(form.utmCampaign(), 100));
        prospects.save(prospect);
        audit.recordAnonymous(AuditAction.CREATE, "Prospect", prospect.getId(), "Registro de interés " + current.getProcessYear());

        String school = schools.findSingleton().map(School::getName).orElse("El colegio");
        publisher.publishEvent(new OutgoingMail(email, "Gracias por tu interés en " + school, """
                Hola %s:

                Recibimos tu interés en %s para el año %d%s.

                %s
                Toda la información del proceso: %s

                %s
                """.formatted(name, school, current.getProcessYear(), level == null ? "" : " (" + level.getName() + ")",
                current.getMode() == AdmissionMode.SAE
                        ? "La postulación se hace en el Sistema de Admisión Escolar del Mineduc: " + current.getSaeUrl() + "\n"
                        : "",
                app.url("/admision"), school)));
        for (UserAccount staff : admissionsStaff()) {
            publisher.publishEvent(new OutgoingMail(staff.getEmail(), "Nuevo registro de interés" + (level == null ? "" : ": " + level.getName()),
                    "Revísalo en " + app.url("/admin/admissions/prospects/" + prospect.getId()) + "\n"));
        }
        return prospect;
    }

    // --- Panel: configuración --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdmissionSettings current() {
        return settings.findSingleton().orElseThrow(() -> new IllegalStateException("Falta la configuración de admisión"));
    }

    @Transactional
    public void updateSettings(AdmissionMode mode, int processYear, String saeUrl, String introText, boolean showVacancies) {
        AdmissionSettings current = current();
        int year = time.today().getYear();
        if (processYear < year - 1 || processYear > year + 2) {
            throw new RuleViolation("El año del proceso debe estar entre " + (year - 1) + " y " + (year + 2));
        }
        if (mode == AdmissionMode.OWN && !features.on(Feature.OWN_ADMISSIONS)) {
            throw new RuleViolation("La admisión propia requiere el módulo de Admisión Pro");
        }
        String url = saeUrl == null || saeUrl.isBlank() ? AdmissionSettings.SAE_URL : saeUrl.strip();
        if (!SafeUrls.isAllowed(url) || !SafeUrls.isExternal(url)) {
            throw new RuleViolation("El enlace al SAE debe ser una dirección https://");
        }
        current.switchMode(mode == null ? current.getMode() : mode);
        current.setProcessYear(processYear);
        current.setSaeUrl(url);
        current.setIntroText(introText == null || introText.isBlank() ? null : HtmlSanitizer.richText(introText));
        current.setShowVacancies(showVacancies);
        audit.record(AuditAction.UPDATE, "AdmissionSettings", 1, "Proceso " + processYear + ", modo " + current.getMode());
    }

    /** Niveles con su regla y vacantes para el año del proceso. */
    @Transactional(readOnly = true)
    public List<LevelInfo> levels() {
        return levelInfo(current());
    }

    /** Guarda, por nivel, si recibe postulantes, el rango de nacimiento (ADM-08) y las vacantes (ADM-07). */
    @Transactional
    public void saveLevel(long gradeLevelId, boolean open, LocalDate bornFrom, LocalDate bornUntil, Integer seats) {
        AdmissionSettings current = current();
        GradeLevel level = levels.findById(gradeLevelId).orElseThrow(() -> new NotFound("El nivel no existe"));
        if (bornFrom != null && bornUntil != null && bornUntil.isBefore(bornFrom)) {
            throw new RuleViolation("La fecha de nacimiento máxima no puede ser anterior a la mínima");
        }
        if (seats != null && seats < 0) {
            throw new RuleViolation("Las vacantes no pueden ser negativas");
        }
        List<AdmissionRules.LevelRule> rules = new ArrayList<>(current.getRules().levels().stream()
                .filter(r -> r.gradeLevelId() != gradeLevelId).toList());
        rules.add(new AdmissionRules.LevelRule(gradeLevelId, open, bornFrom, bornUntil));
        current.setRules(new AdmissionRules(rules));

        Optional<Vacancy> vacancy = vacancies.findByAcademicYearOrderByGradeLevel_SortOrderAsc(current.getProcessYear()).stream()
                .filter(v -> v.getGradeLevel().equals(level)).findFirst();
        if (seats == null) {
            vacancy.ifPresent(vacancies::delete);
        } else if (vacancy.isPresent()) {
            vacancy.get().updateSeats(seats);
        } else {
            vacancies.save(new Vacancy(level, current.getProcessYear(), seats));
        }
        audit.record(AuditAction.UPDATE, "AdmissionSettings", 1, level.getName() + (open ? " abierto" : " cerrado")
                + (seats == null ? "" : ", " + seats + " vacantes"));
    }

    @Transactional(readOnly = true)
    public List<AdmissionMilestone> milestones() {
        return milestones.findByProcessYearOrderByStartsOnAscSortOrderAsc(current().getProcessYear());
    }

    @Transactional
    public void addMilestone(String name, LocalDate startsOn, LocalDate endsOn, String description, String linkUrl) {
        String cleanName = required(name, 150, "El hito necesita un nombre de hasta 150 caracteres");
        if (startsOn == null) {
            throw new RuleViolation("Indica la fecha de inicio");
        }
        if (endsOn != null && endsOn.isBefore(startsOn)) {
            throw new RuleViolation("El hito no puede terminar antes de empezar");
        }
        String link = linkUrl == null || linkUrl.isBlank() ? null : linkUrl.strip();
        if (link != null && !SafeUrls.isAllowed(link)) {
            throw new RuleViolation("Enlace no permitido: usa una ruta del sitio (/admision) o una dirección https://");
        }
        AdmissionMilestone milestone = new AdmissionMilestone(current().getProcessYear(), cleanName, startsOn, endsOn, 0);
        milestone.setDescription(optional(description, 500));
        milestone.setLinkUrl(link);
        milestones.save(milestone);
        audit.record(AuditAction.CREATE, "AdmissionMilestone", milestone.getId(), cleanName);
    }

    @Transactional
    public void deleteMilestone(long id) {
        AdmissionMilestone milestone = milestones.findById(id).orElseThrow(() -> new NotFound("El hito no existe"));
        milestones.delete(milestone);
        audit.record(AuditAction.DELETE, "AdmissionMilestone", id, milestone.getName());
    }

    // --- Panel: registros de interés -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Prospect> prospects() {
        return prospects.findTop300ByOrderByCreatedAtDesc();
    }

    @Transactional
    public Prospect prospect(long id) {
        Prospect prospect = prospects.findWithLevelById(id).orElseThrow(() -> new NotFound("El registro no existe"));
        audit.record(AuditAction.VIEW_PERSONAL_DATA, "Prospect", id, null);
        return prospect;
    }

    @Transactional
    public void advance(long id, ProspectStage stage) {
        Prospect prospect = prospects.findById(id).orElseThrow(() -> new NotFound("El registro no existe"));
        try {
            prospect.advanceTo(stage);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new RuleViolation(e.getMessage());
        }
        audit.record(AuditAction.UPDATE, "Prospect", id, "Etapa " + stage);
    }

    @Transactional
    public void delete(long id) {
        Prospect prospect = prospects.findById(id).orElseThrow(() -> new NotFound("El registro no existe"));
        prospects.delete(prospect);
        audit.record(AuditAction.DELETE, "Prospect", id, "Registro de interés eliminado");
    }

    // --- Apoyo -----------------------------------------------------------------------------------------------

    private List<LevelInfo> levelInfo(AdmissionSettings current) {
        Map<Long, Integer> seats = vacancies.findByAcademicYearOrderByGradeLevel_SortOrderAsc(current.getProcessYear()).stream()
                .collect(Collectors.toMap(v -> v.getGradeLevel().getId(), Vacancy::getSeats));
        Map<Long, AdmissionRules.LevelRule> rules = current.getRules().levels().stream()
                .collect(Collectors.toMap(AdmissionRules.LevelRule::gradeLevelId, Function.identity(), (a, b) -> b));
        return levels.findAllByOrderBySortOrderAsc().stream().map(level -> {
            AdmissionRules.LevelRule rule = rules.get(level.getId());
            return new LevelInfo(level, rule != null && rule.open(), rule == null ? null : rule.bornFrom(),
                    rule == null ? null : rule.bornUntil(), seats.get(level.getId()));
        }).toList();
    }

    private List<UserAccount> admissionsStaff() {
        return users.findAllByOrderByNameAsc().stream().filter(u -> u.canLogIn() && u.can(Permission.ADMISSIONS)).toList();
    }

    private static String required(String value, int max, String problem) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw new RuleViolation(problem);
        }
        return value.strip();
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new RuleViolation("Un campo supera el largo permitido");
        }
        return value.strip();
    }
}
