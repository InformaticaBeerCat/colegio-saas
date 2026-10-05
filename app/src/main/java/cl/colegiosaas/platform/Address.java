package cl.colegiosaas.platform;

import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Dirección física (PUB-04). Se guarda en columnas de la tabla dueña. */
@Embeddable
public record Address(
        String street,
        String commune,
        String region,
        Double latitude,
        Double longitude) {

    /** "Providencia, Región Metropolitana": comuna y región, las que estén. */
    public String locality() {
        return Stream.of(commune, region)
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(", "));
    }
}
