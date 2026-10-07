package cl.colegiosaas.scheduling;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuración de la agenda: tipos de cita y quién los atiende (AGE-01), disponibilidad semanal, bloqueos y
 * feriados (AGE-02).
 */
@Service
public class AgendaConfigService {

    private final AppointmentTypeRepository types;
    private final AvailabilityRuleRepository rules;
    private final AvailabilityBlockRepository blocks;
    private final HolidayRepository holidays;
    private final AppointmentRepository appointments;
    private final UserAccountRepository users;
    private final AuditTrail audit;

    AgendaConfigService(AppointmentTypeRepository types, AvailabilityRuleRepository rules, AvailabilityBlockRepository blocks,
                        HolidayRepository holidays, AppointmentRepository appointments, UserAccountRepository users,
                        AuditTrail audit) {
        this.types = types;
        this.rules = rules;
        this.blocks = blocks;
        this.holidays = holidays;
        this.appointments = appointments;
        this.users = users;
        this.audit = audit;
    }

    // --- Tipos de cita (AGE-01) -----------------------------------------------------------------------------

    /** Datos editables de un tipo de cita. */
    public record TypeDraft(String name, String description, int durationMinutes, int bufferMinutes,
                            AppointmentAudience audience, boolean inPerson, boolean online, boolean active,
                            Set<Long> hostIds) {

        public TypeDraft {
            hostIds = hostIds == null ? Set.of() : Set.copyOf(hostIds);
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentType> allTypes() {
        return types.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public AppointmentType type(long id) {
        return types.findWithHostsById(id).orElseThrow(() -> new NotFound("El tipo de cita no existe"));
    }

    /** Funcionarios que pueden atender citas: tienen agenda propia o administran la de todos. */
    @Transactional(readOnly = true)
    public List<UserAccount> possibleHosts() {
        return users.findAllByOrderByNameAsc().stream()
                .filter(u -> u.canLogIn() && (u.can(Permission.SCHEDULING_OWN) || u.can(Permission.SCHEDULING_ALL)))
                .toList();
    }

    @Transactional
    public AppointmentType saveType(Long id, TypeDraft draft) {
        String name = draft.name() == null ? "" : draft.name().strip();
        if (name.isEmpty() || name.length() > 120) {
            throw new RuleViolation("El tipo de cita necesita un nombre de hasta 120 caracteres");
        }
        if (draft.durationMinutes() < 5 || draft.durationMinutes() > 480) {
            throw new RuleViolation("La duración debe estar entre 5 minutos y 8 horas");
        }
        if (draft.bufferMinutes() < 0 || draft.bufferMinutes() > 120) {
            throw new RuleViolation("La pausa entre citas debe estar entre 0 y 120 minutos");
        }
        if (draft.audience() == null) {
            throw new RuleViolation("Indica quién puede reservar");
        }
        if (!draft.inPerson() && !draft.online()) {
            throw new RuleViolation("La cita debe ser presencial, en línea o ambas");
        }
        Set<UserAccount> hosts = new HashSet<>(users.findAllById(draft.hostIds()));
        if (hosts.stream().anyMatch(h -> !h.can(Permission.SCHEDULING_OWN) && !h.can(Permission.SCHEDULING_ALL))) {
            throw new RuleViolation("Solo atienden citas funcionarios con agenda");
        }
        if (draft.active() && hosts.isEmpty()) {
            throw new RuleViolation("Asigna al menos un funcionario que atienda este tipo de cita");
        }
        AppointmentType type = id == null ? new AppointmentType(name, draft.durationMinutes(), draft.audience()) : type(id);
        type.setName(name);
        type.setDescription(draft.description() == null || draft.description().isBlank() ? null : draft.description().strip());
        type.changeDuration(draft.durationMinutes());
        type.setBufferMinutes(draft.bufferMinutes());
        type.setAudience(draft.audience());
        type.allowModes(draft.inPerson(), draft.online());
        type.setActive(draft.active());
        Set.copyOf(type.getHosts()).stream().filter(h -> !hosts.contains(h)).forEach(type::removeHost);
        hosts.forEach(type::addHost);
        types.save(type);
        audit.record(id == null ? AuditAction.CREATE : AuditAction.UPDATE, "AppointmentType", type.getId(), name);
        return type;
    }

    /** Un tipo con citas no se borra (son datos de personas): se desactiva. */
    @Transactional
    public void deleteType(long id) {
        AppointmentType type = type(id);
        if (appointments.existsByAppointmentType(type)) {
            throw new RuleViolation("Este tipo tiene citas registradas: desactívalo en vez de eliminarlo");
        }
        rules.findAll().stream().filter(r -> type.equals(r.getAppointmentType())).forEach(rules::delete);
        types.delete(type);
        audit.record(AuditAction.DELETE, "AppointmentType", id, type.getName());
    }

    // --- Disponibilidad semanal (AGE-02) --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AvailabilityRule> rulesOf(long hostId) {
        return rules.findWithTypeByHostOrderByDayOfWeekAscStartTimeAsc(user(hostId));
    }

    /** Tipos que atiende el funcionario: para limitar una ventana a uno de ellos. */
    @Transactional(readOnly = true)
    public List<AppointmentType> typesHostedBy(long hostId) {
        UserAccount host = user(hostId);
        return types.findAllByOrderByNameAsc().stream().filter(t -> t.isHostedBy(host)).toList();
    }

    @Transactional
    public void addRule(long hostId, Collection<DayOfWeek> days, LocalTime start, LocalTime end, Long typeId,
                        LocalDate validFrom, LocalDate validUntil) {
        UserAccount host = user(hostId);
        if (days == null || days.isEmpty()) {
            throw new RuleViolation("Elige al menos un día");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new RuleViolation("La hora de término debe ser posterior a la de inicio");
        }
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new RuleViolation("La vigencia no puede terminar antes de empezar");
        }
        AppointmentType type = typeId == null ? null : type(typeId);
        if (type != null && !type.isHostedBy(host)) {
            throw new RuleViolation("Ese tipo de cita no lo atiende esta persona");
        }
        for (DayOfWeek day : days) {
            AvailabilityRule rule = new AvailabilityRule(host, day, start, end);
            rule.setAppointmentType(type);
            rule.setValidFrom(validFrom);
            rule.setValidUntil(validUntil);
            rules.save(rule);
        }
        audit.record(AuditAction.CREATE, "AvailabilityRule", hostId, days.size() + " día(s) " + start + "–" + end);
    }

    /** Quitar una ventana no cancela las citas ya tomadas en ella. */
    @Transactional
    public void deleteRule(long ruleId, long hostId) {
        AvailabilityRule rule = rules.findById(ruleId).orElseThrow(() -> new NotFound("La disponibilidad no existe"));
        if (!rule.getHost().getId().equals(hostId)) {
            throw new NotFound("La disponibilidad no existe");
        }
        rules.delete(rule);
        audit.record(AuditAction.DELETE, "AvailabilityRule", ruleId, rule.getDayOfWeek().toString());
    }

    // --- Bloqueos -------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AvailabilityBlock> upcomingBlocks(Long hostId, LocalDateTime from) {
        return hostId == null ? blocks.findByEndsAtAfterOrderByStartsAtAsc(from) : blocks.findUpcomingFor(user(hostId), from);
    }

    /** @param hostId nulo = bloquea a todo el colegio (jornada de reflexión, paro) */
    @Transactional
    public void addBlock(Long hostId, LocalDateTime start, LocalDateTime end, String reason) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new RuleViolation("El bloqueo debe terminar después de empezar");
        }
        String cleanReason = reason == null || reason.isBlank() ? null : reason.strip();
        if (cleanReason != null && cleanReason.length() > 200) {
            throw new RuleViolation("El motivo puede tener hasta 200 caracteres");
        }
        AvailabilityBlock block = blocks.save(new AvailabilityBlock(hostId == null ? null : user(hostId), start, end, cleanReason));
        audit.record(AuditAction.CREATE, "AvailabilityBlock", block.getId(), (hostId == null ? "Todo el colegio" : "Funcionario") + " " + start);
    }

    /**
     * @param hostId quien lo borra, para que no borre los de otro; nulo = puede borrar cualquiera (gestión de todo el colegio)
     */
    @Transactional
    public void deleteBlock(long blockId, Long hostId) {
        AvailabilityBlock block = blocks.findById(blockId).orElseThrow(() -> new NotFound("El bloqueo no existe"));
        if (hostId != null && (block.getHost() == null || !block.getHost().getId().equals(hostId))) {
            throw new NotFound("El bloqueo no existe");
        }
        blocks.delete(block);
        audit.record(AuditAction.DELETE, "AvailabilityBlock", blockId, null);
    }

    // --- Feriados -------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Holiday> holidays(int year) {
        return holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional
    public void addHoliday(LocalDate date, String name, boolean mandatory) {
        if (date == null) {
            throw new RuleViolation("Indica la fecha");
        }
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > 120) {
            throw new RuleViolation("El feriado necesita un nombre de hasta 120 caracteres");
        }
        if (holidays.existsByHolidayDate(date)) {
            throw new RuleViolation("Ya hay un feriado ese día");
        }
        holidays.save(new Holiday(date, cleanName, mandatory));
        audit.record(AuditAction.CREATE, "Holiday", date, cleanName);
    }

    /** Guarda los feriados que falten; devuelve cuántos agregó. */
    @Transactional
    public int importHolidays(List<Holiday> fetched) {
        int added = 0;
        for (Holiday holiday : fetched) {
            if (!holidays.existsByHolidayDate(holiday.getHolidayDate())) {
                holidays.save(holiday);
                added++;
            }
        }
        audit.record(AuditAction.CREATE, "Holiday", null, added + " feriado(s) importados de la fuente oficial");
        return added;
    }

    @Transactional
    public void deleteHoliday(long id) {
        Holiday holiday = holidays.findById(id).orElseThrow(() -> new NotFound("El feriado no existe"));
        holidays.delete(holiday);
        audit.record(AuditAction.DELETE, "Holiday", holiday.getHolidayDate(), holiday.getName());
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow(() -> new NotFound("El funcionario no existe"));
    }
}
