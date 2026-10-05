package cl.colegiosaas.admissions;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reglas del proceso configurables desde el panel, no programadas a mano (ADM-08): el modelo del
 * SAE puede cambiar por ley. Se guardan como JSON en {@code admission_settings.rules}.
 */
public record AdmissionRules(List<LevelRule> levels) {

    public AdmissionRules {
        levels = levels == null ? List.of() : List.copyOf(levels);
    }

    public static AdmissionRules none() {
        return new AdmissionRules(List.of());
    }

    public Optional<LevelRule> forGradeLevel(long gradeLevelId) {
        return levels.stream().filter(rule -> rule.gradeLevelId() == gradeLevelId).findFirst();
    }

    /**
     * Regla por nivel: si recibe postulantes y el rango de fechas de nacimiento admitido
     * (la edad mínima se mide a una fecha de corte, p. ej. 31 de marzo).
     */
    public record LevelRule(long gradeLevelId, boolean open, LocalDate bornFrom, LocalDate bornUntil) {

        public boolean admitsBirthDate(LocalDate birthDate) {
            return open
                    && (bornFrom == null || !birthDate.isBefore(bornFrom))
                    && (bornUntil == null || !birthDate.isAfter(bornUntil));
        }
    }
}
