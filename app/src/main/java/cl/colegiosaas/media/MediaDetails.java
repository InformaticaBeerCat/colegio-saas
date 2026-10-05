package cl.colegiosaas.media;

import java.time.LocalDate;
import java.util.Set;

/** Datos editables de un medio: accesibilidad, pie, créditos y organización. */
public record MediaDetails(String altText, String caption, String credits, LocalDate takenOn, Long folderId, Set<String> tags) {

    public MediaDetails {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
