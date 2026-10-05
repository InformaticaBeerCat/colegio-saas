package cl.colegiosaas.platform;

import jakarta.persistence.Embeddable;

/** Dirección física (PUB-04). Se guarda en columnas de la tabla dueña. */
@Embeddable
public record Address(
        String street,
        String commune,
        String region,
        Double latitude,
        Double longitude) {
}
