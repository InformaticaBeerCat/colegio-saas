package cl.colegiosaas.shared.persistence;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Raíz de todas las entidades: id autoincremental, bloqueo optimista y marcas de tiempo.
 * Los ids son internos; hacia afuera se exponen slugs o tokens aleatorios.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Evita que dos editores se pisen cambios: Hibernate rechaza la segunda escritura. */
    @Version
    private long version;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // equals/hashCode por id, tolerante a proxies de Hibernate y estable antes de persistir.
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClassLazy(this) != Hibernate.getClassLazy(o)) return false;
        Long otherId = ((BaseEntity) o).getId();
        return getId() != null && getId().equals(otherId);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClassLazy(this).hashCode();
    }
}
