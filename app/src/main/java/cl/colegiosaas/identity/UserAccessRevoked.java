package cl.colegiosaas.identity;

/**
 * Evento: los permisos de una persona cambiaron o se cortó su acceso (baja, cambio de roles, nueva
 * contraseña). Seguridad lo escucha y cierra sus sesiones abiertas, salvo {@code keepSessionId}.
 */
public record UserAccessRevoked(long userId, String keepSessionId) {

    public UserAccessRevoked(long userId) {
        this(userId, null);
    }
}
