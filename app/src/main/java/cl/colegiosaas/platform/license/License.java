package cl.colegiosaas.platform.license;

import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Plan;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Licencia de una instalación (OPS-05): para qué colegio y dominio es, qué plan y qué add-ons incluye y hasta
 * cuándo vale. La emite el proveedor firmada; la instalación solo la verifica.
 *
 * @param domain dominio del sitio (sin www); la instalación en producción debe servirse en él
 */
public record License(String id, String school, String domain, Plan plan, Set<Feature> addons, LocalDate issuedOn,
                      LocalDate expiresOn) {

    /** Días de gracia tras el vencimiento: el sitio sigue completo mientras se renueva. */
    public static final int GRACE_DAYS = 30;

    public License {
        addons = addons == null || addons.isEmpty() ? Set.of() : Set.copyOf(addons);
    }

    /** Módulos que la licencia permite activar: los del plan más los add-ons. */
    public Set<Feature> allowedFeatures() {
        EnumSet<Feature> allowed = EnumSet.copyOf(plan.includedFeatures());
        allowed.addAll(addons);
        return allowed;
    }

    public boolean isExpired(LocalDate today) {
        return today.isAfter(expiresOn);
    }

    /** Pasada la gracia, solo quedan los módulos del plan Base. */
    public boolean isBeyondGrace(LocalDate today) {
        return today.isAfter(expiresOn.plusDays(GRACE_DAYS));
    }
}
