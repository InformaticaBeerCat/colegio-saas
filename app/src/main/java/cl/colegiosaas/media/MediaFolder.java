package cl.colegiosaas.media;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Carpeta de la biblioteca de medios (MED-01). Sin {@code parent} es una carpeta raíz. */
@Entity
@Table(name = "media_folder")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaFolder extends BaseEntity {

    @NotBlank
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private MediaFolder parent;

    public MediaFolder(String name, MediaFolder parent) {
        this.name = name;
        this.parent = parent;
    }
}
