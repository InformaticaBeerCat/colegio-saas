package cl.colegiosaas.media;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/** Etiqueta libre para buscar medios: evento, año, curso (MED-01). Se guarda en minúsculas. */
@Entity
@Table(name = "media_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaTag extends BaseEntity {

    @NotBlank
    private String name;

    public MediaTag(String name) {
        this.name = normalize(name);
    }

    public static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
