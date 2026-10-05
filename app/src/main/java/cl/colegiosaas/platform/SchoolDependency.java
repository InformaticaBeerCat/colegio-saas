package cl.colegiosaas.platform;

/** Dependencia administrativa del establecimiento según Mineduc. */
public enum SchoolDependency {
    MUNICIPAL,
    SLEP,
    /** Particular subvencionado. */
    PRIVATE_SUBSIDIZED,
    /** Particular pagado. */
    PRIVATE_PAID,
    /** Administración delegada. */
    DELEGATED_ADMINISTRATION;

    /** Los particulares pagados tienen proceso de admisión propio; el resto postula vía SAE. */
    public boolean admitsViaSae() {
        return this != PRIVATE_PAID;
    }

    /** WCAG 2.x AA es exigible en la práctica a establecimientos públicos (Ley 20.422, Decreto 1/2015). */
    public boolean requiresAccessibilityAA() {
        return this == MUNICIPAL || this == SLEP;
    }
}
