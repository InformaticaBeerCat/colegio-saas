package cl.colegiosaas.page;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Entrada del menú principal o del pie (CFG-05). Apunta a una página propia o a una URL,
 * nunca a ambas (la tabla lo exige con un CHECK). Los submenús usan {@code parent}.
 */
@Entity
@Table(name = "menu_item")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItem extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private MenuLocation menu;

    @NotBlank
    private String label;

    /** Si la página se borra, primero hay que sacarla del menú (la llave foránea lo impide). */
    @ManyToOne(fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Page page;

    @Setter(AccessLevel.NONE)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    private MenuItem parent;

    private int sortOrder;

    private MenuItem(MenuLocation menu, String label, Page page, String url, int sortOrder) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.label = label;
        this.page = page;
        this.url = url;
        this.sortOrder = sortOrder;
    }

    public static MenuItem toPage(MenuLocation menu, String label, Page page, int sortOrder) {
        return new MenuItem(menu, label, Objects.requireNonNull(page, "page"), null, sortOrder);
    }

    public static MenuItem toUrl(MenuLocation menu, String label, String url, int sortOrder) {
        return new MenuItem(menu, label, null, Objects.requireNonNull(url, "url"), sortOrder);
    }

    public void pointTo(Page newPage) {
        page = Objects.requireNonNull(newPage, "page");
        url = null;
    }

    public void pointTo(String newUrl) {
        url = Objects.requireNonNull(newUrl, "url");
        page = null;
    }
}
