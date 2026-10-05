package cl.colegiosaas.shared.tenant;

import java.util.Optional;

/**
 * Colegio en el que trabaja el hilo actual.
 *
 * <pre>
 * try (var scope = TenantContext.use(schoolId)) {
 *     service.doSomething();   // todas las consultas quedan filtradas a ese colegio
 * }
 * </pre>
 *
 * Debe fijarse ANTES de abrir la transacción: Hibernate lee el colegio al abrir la sesión.
 */
public final class TenantContext {

    /** Id sin colegio asociado: Hibernate filtra por él y no devuelve nada (falla cerrado). */
    public static final long NONE = -1L;

    /** Id "raíz": desactiva el filtro. Solo para tareas de plataforma (Super Admin, jobs). */
    public static final long PLATFORM = 0L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Scope use(long schoolId) {
        if (schoolId <= 0) {
            throw new IllegalArgumentException("Id de colegio inválido: " + schoolId);
        }
        return set(schoolId);
    }

    public static Scope usePlatform() {
        return set(PLATFORM);
    }

    /** Colegio actual; vacío si no hay ninguno o si se está en modo plataforma. */
    public static Optional<Long> currentSchoolId() {
        Long id = CURRENT.get();
        return id == null || id == PLATFORM ? Optional.empty() : Optional.of(id);
    }

    static long idForHibernate() {
        Long id = CURRENT.get();
        return id != null ? id : NONE;
    }

    private static Scope set(long id) {
        Long previous = CURRENT.get();
        CURRENT.set(id);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    /** Al cerrarse restaura el colegio anterior (permite anidar ámbitos). */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
