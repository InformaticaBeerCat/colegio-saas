package cl.colegiosaas.setup;

import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.SchoolDependency;

/** Datos que entrega el asistente de primer arranque. */
public record Installation(
        String schoolName,
        String rbd,
        SchoolDependency dependency,
        Plan plan,
        String contactEmail,
        String adminName,
        String adminEmail,
        String adminPassword) {
}
