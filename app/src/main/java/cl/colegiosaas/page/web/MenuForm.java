package cl.colegiosaas.page.web;

import cl.colegiosaas.page.MenuItem;
import cl.colegiosaas.page.MenuLocation;
import cl.colegiosaas.page.MenuService.MenuTarget;
import lombok.Getter;
import lombok.Setter;

/** Entrada de menú: apunta a una página propia o a un enlace, según {@code targetType}. */
@Getter
@Setter
public class MenuForm {

    private MenuLocation menu = MenuLocation.HEADER;
    private String label;
    private String targetType = "page";
    private Long pageId;
    private String url;
    private Long parentId;

    static MenuForm of(MenuItem item) {
        MenuForm form = new MenuForm();
        form.menu = item.getMenu();
        form.label = item.getLabel();
        form.targetType = item.getPage() != null ? "page" : "url";
        form.pageId = item.getPage() == null ? null : item.getPage().getId();
        form.url = item.getUrl();
        form.parentId = item.getParent() == null ? null : item.getParent().getId();
        return form;
    }

    MenuTarget toTarget() {
        boolean toPage = "page".equals(targetType);
        return new MenuTarget(label, toPage ? pageId : null, toPage ? null : url, parentId);
    }
}
