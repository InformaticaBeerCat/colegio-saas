package cl.colegiosaas.news;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "news_category")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsCategory extends BaseEntity {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
    private String slug;

    public NewsCategory(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }
}
