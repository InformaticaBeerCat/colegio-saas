package cl.colegiosaas.platform.license;

import cl.colegiosaas.platform.Plan;

import java.time.LocalDate;
import java.util.Set;

/** Licencias de ejemplo para tests de otros paquetes. */
public final class LicenseCodecTestAccess {

    private LicenseCodecTestAccess() {
    }

    public static License license(String domain) {
        return new License("lic-test", "Colegio San José", domain, Plan.COMMUNITY, Set.of(), LocalDate.of(2026, 1, 1),
                LocalDate.of(2099, 12, 31));
    }
}
