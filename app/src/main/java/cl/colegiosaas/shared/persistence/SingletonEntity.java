package cl.colegiosaas.shared.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Entidad de la que existe una sola fila por instalación (perfil del colegio, configuración del sitio…).
 * El id es siempre {@link #ID}; la tabla lo refuerza con {@code CHECK (id = 1)}.
 */
@MappedSuperclass
@Getter
public abstract class SingletonEntity {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    /** Wrapper a propósito: nulo indica a Spring Data que la fila aún no existe (persist en vez de merge). */
    @Version
    private Long version;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
