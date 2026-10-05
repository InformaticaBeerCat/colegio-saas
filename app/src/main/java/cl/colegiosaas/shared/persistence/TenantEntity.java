package cl.colegiosaas.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Entidad que pertenece a un colegio. Hibernate completa {@code school_id} al insertar
 * y agrega {@code where school_id = ?} a cada consulta según {@code TenantContext}.
 * Ojo: las consultas SQL nativas NO se filtran.
 */
@MappedSuperclass
@Getter
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(nullable = false, updatable = false)
    private Long schoolId;
}
