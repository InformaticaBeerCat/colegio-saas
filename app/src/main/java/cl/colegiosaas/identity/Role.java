package cl.colegiosaas.identity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static cl.colegiosaas.identity.Permission.*;

/** Roles de la instalación (sección 2 de requerimientos). Cada rol es un paquete de permisos. */
public enum Role {
    /** Proveedor: instala, actualiza, da soporte y edita CSS personalizado (CFG-09). */
    SUPER_ADMIN(true, EnumSet.allOf(Permission.class)),
    SCHOOL_ADMIN(true, EnumSet.complementOf(EnumSet.of(PLATFORM))),
    /** Comunicaciones y secretaría: publica contenido; las noticias pasan por aprobación. */
    EDITOR(false, EnumSet.of(PANEL_ACCESS, PAGES, NEWS_EDIT, ANNOUNCEMENTS, CALENDAR, GENERAL_INFO,
            DOCUMENTS, MEDIA_UPLOAD, EVENTS, INQUIRIES)),
    /** Gestor de agenda: admisión, inspectoría, profesores jefe. */
    SCHEDULE_MANAGER(false, EnumSet.of(PANEL_ACCESS, SCHEDULING_OWN)),
    /** Gestor de consentimientos: autorizaciones de imagen y revisión de fotos. */
    CONSENT_MANAGER(true, EnumSet.of(PANEL_ACCESS, MEDIA_REVIEW, STUDENTS_AND_CONSENTS)),
    /** Editor satélite del Centro de Padres: publica solo en su sección, con aprobación. */
    PARENTS_CENTER_EDITOR(false, EnumSet.of(PANEL_ACCESS, NEWS_EDIT, MEDIA_UPLOAD, SECTION_PARENTS_CENTER)),
    /** Editor satélite del Centro de Estudiantes. */
    STUDENT_COUNCIL_EDITOR(false, EnumSet.of(PANEL_ACCESS, NEWS_EDIT, MEDIA_UPLOAD, SECTION_STUDENT_COUNCIL)),
    /** Apoderado: usa la zona comunidad (v2), no el panel. */
    GUARDIAN(false, EnumSet.noneOf(Permission.class)),
    STUDENT(false, EnumSet.noneOf(Permission.class));

    private final boolean requiresMfa;
    private final Set<Permission> permissions;

    Role(boolean requiresMfa, EnumSet<Permission> permissions) {
        this.requiresMfa = requiresMfa;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    /** USR-02: MFA obligatorio para administradores y para quien ve datos de menores. */
    public boolean requiresMfa() {
        return requiresMfa;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** Roles que se asignan desde el panel (los de la comunidad llegan en v2). */
    public boolean isStaff() {
        return permissions.contains(PANEL_ACCESS);
    }
}
