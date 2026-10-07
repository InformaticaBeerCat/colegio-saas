package cl.colegiosaas.scheduling;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.platform.SchoolTime;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calcula las horas que se pueden reservar (AGE-02, AGE-03): las ventanas semanales de cada funcionario,
 * en bloques de la duración del tipo de cita más su pausa, menos feriados, bloqueos, citas ya tomadas y
 * lo que está demasiado cerca. Todo en hora del colegio.
 */
@Component
@EnableConfigurationProperties(AgendaProperties.class)
public class SlotFinder {

    private final AvailabilityRuleRepository rules;
    private final AvailabilityBlockRepository blocks;
    private final AppointmentRepository appointments;
    private final HolidayRepository holidays;
    private final AgendaProperties properties;
    private final SchoolTime time;

    SlotFinder(AvailabilityRuleRepository rules, AvailabilityBlockRepository blocks, AppointmentRepository appointments,
               HolidayRepository holidays, AgendaProperties properties, SchoolTime time) {
        this.rules = rules;
        this.blocks = blocks;
        this.appointments = appointments;
        this.holidays = holidays;
        this.properties = properties;
        this.time = time;
    }

    /** Una hora disponible con un funcionario. */
    public record Slot(long hostId, String hostName, LocalDateTime start, LocalDateTime end) {
    }

    /** Horas libres del tipo de cita en el horizonte configurado, de todos sus funcionarios (o de uno). */
    @Transactional(readOnly = true)
    public List<Slot> available(AppointmentType type, UserAccount onlyHost) {
        LocalDate today = time.today();
        return available(type, onlyHost, today, today.plusDays(properties.horizonDays()));
    }

    @Transactional(readOnly = true)
    public List<Slot> available(AppointmentType type, UserAccount onlyHost, LocalDate from, LocalDate to) {
        if (!type.isActive()) {
            return List.of();
        }
        LocalDateTime earliest = time.now().plus(properties.minNotice());
        Set<LocalDate> closed = new HashSet<>();
        holidays.findByHolidayDateBetween(from, to).forEach(h -> closed.add(h.getHolidayDate()));
        List<Slot> slots = new ArrayList<>();
        for (UserAccount host : type.getHosts()) {
            if (onlyHost != null && !host.equals(onlyHost) || !host.canLogIn()) {
                continue;
            }
            LocalDateTime rangeStart = from.atStartOfDay();
            LocalDateTime rangeEnd = to.plusDays(1).atStartOfDay();
            List<AvailabilityBlock> hostBlocks = blocks.findAffecting(host, rangeStart, rangeEnd);
            List<Appointment> taken = appointments.findActiveForHost(host, rangeStart.minusDays(1), rangeEnd.plusDays(1));
            List<AvailabilityRule> windows = rules.findByHost(host).stream()
                    .filter(r -> r.getAppointmentType() == null || r.getAppointmentType().equals(type))
                    .toList();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                if (closed.contains(day)) {
                    continue;
                }
                for (AvailabilityRule window : windows) {
                    if (!window.appliesOn(day)) {
                        continue;
                    }
                    LocalDateTime start = day.atTime(window.getStartTime());
                    LocalDateTime windowEnd = day.atTime(window.getEndTime());
                    while (!start.plus(type.duration()).isAfter(windowEnd)) {
                        LocalDateTime end = start.plus(type.duration());
                        if (!start.isBefore(earliest) && isFree(type, start, end, hostBlocks, taken)) {
                            slots.add(new Slot(host.getId(), host.getName(), start, end));
                        }
                        start = end.plusMinutes(type.getBufferMinutes());
                    }
                }
            }
        }
        // Si dos ventanas del mismo funcionario se solapan, no se repite la hora.
        return slots.stream().distinct()
                .sorted(Comparator.comparing(Slot::start).thenComparing(Slot::hostName))
                .toList();
    }

    /** ¿Sigue libre esa hora con ese funcionario? Se vuelve a revisar al reservar, con la fila bloqueada. */
    @Transactional(readOnly = true)
    public boolean isAvailable(AppointmentType type, UserAccount host, LocalDateTime start, Long ignoreAppointmentId) {
        return available(type, host, start.toLocalDate(), start.toLocalDate()).stream()
                .anyMatch(s -> s.start().equals(start))
                || (ignoreAppointmentId != null && freeIgnoring(type, host, start, ignoreAppointmentId));
    }

    /** Al reprogramar, la cita propia no ocupa la hora a la que se quiere mover. */
    private boolean freeIgnoring(AppointmentType type, UserAccount host, LocalDateTime start, long appointmentId) {
        LocalDate day = start.toLocalDate();
        if (holidays.existsByHolidayDate(day) || start.isBefore(time.now().plus(properties.minNotice()))) {
            return false;
        }
        LocalDateTime end = start.plus(type.duration());
        boolean inWindow = rules.findByHost(host).stream()
                .filter(r -> r.getAppointmentType() == null || r.getAppointmentType().equals(type))
                .anyMatch(r -> r.appliesOn(day) && !start.toLocalTime().isBefore(r.getStartTime())
                        && !end.toLocalTime().isAfter(r.getEndTime()) && alignedToGrid(type, r, start));
        if (!inWindow) {
            return false;
        }
        List<Appointment> taken = appointments.findActiveForHost(host, start.minusDays(1), end.plusDays(1)).stream()
                .filter(a -> !a.getId().equals(appointmentId)).toList();
        return isFree(type, start, end, blocks.findAffecting(host, start, end), taken);
    }

    private static boolean alignedToGrid(AppointmentType type, AvailabilityRule rule, LocalDateTime start) {
        long step = type.getDurationMinutes() + type.getBufferMinutes();
        long offset = java.time.Duration.between(start.toLocalDate().atTime(rule.getStartTime()), start).toMinutes();
        return offset >= 0 && offset % step == 0;
    }

    private static boolean isFree(AppointmentType type, LocalDateTime start, LocalDateTime end,
                                  List<AvailabilityBlock> blocks, List<Appointment> taken) {
        if (blocks.stream().anyMatch(b -> b.overlaps(start, end))) {
            return false;
        }
        // La pausa del tipo separa esta cita de las vecinas, de cualquier tipo.
        LocalDateTime paddedStart = start.minusMinutes(type.getBufferMinutes());
        LocalDateTime paddedEnd = end.plusMinutes(type.getBufferMinutes());
        return taken.stream().noneMatch(a -> a.occupies(paddedStart, paddedEnd));
    }
}
