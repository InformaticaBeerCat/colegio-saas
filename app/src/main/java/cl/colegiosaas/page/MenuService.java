package cl.colegiosaas.page;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.web.SafeUrls;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Menú principal y del pie (CFG-05). Dos niveles como máximo: más que eso no se usa bien en un
 * teléfono ni con teclado.
 */
@Service
public class MenuService {

    private static final int MAX_LABEL = 80;

    private final MenuItemRepository items;
    private final PageRepository pages;
    private final AuditTrail audit;

    MenuService(MenuItemRepository items, PageRepository pages, AuditTrail audit) {
        this.items = items;
        this.pages = pages;
        this.audit = audit;
    }

    /** Entradas de un menú para el panel, cada padre seguido de sus hijos. */
    @Transactional(readOnly = true)
    public List<MenuItem> items(MenuLocation menu) {
        List<MenuItem> all = items.findByMenuOrderBySortOrderAscIdAsc(menu);
        List<MenuItem> ordered = new ArrayList<>();
        for (MenuItem top : all.stream().filter(i -> i.getParent() == null).toList()) {
            ordered.add(top);
            all.stream().filter(i -> i.getParent() == top).forEach(ordered::add);
        }
        return ordered;
    }

    /** Entradas que pueden ser padre de otra: las de primer nivel del mismo menú. */
    @Transactional(readOnly = true)
    public List<MenuItem> topLevel(MenuLocation menu) {
        return items.findByMenuOrderBySortOrderAscIdAsc(menu).stream().filter(i -> i.getParent() == null).toList();
    }

    @Transactional(readOnly = true)
    public MenuItem get(long id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("La entrada de menú no existe"));
    }

    /**
     * Árbol del menú para el sitio. Las entradas a páginas no publicadas se omiten, salvo en la vista
     * previa; un padre oculto que tiene hijos visibles queda como rótulo del submenú, sin enlace.
     *
     * @param pageLink  arma el enlace de una página (público o de vista previa)
     * @param preview   muestra también las páginas en borrador
     * @param currentId página que se está viendo, para marcarla; puede ser nulo
     */
    @Transactional(readOnly = true)
    public List<MenuEntry> tree(MenuLocation menu, Function<Page, String> pageLink, boolean preview, Long currentId) {
        List<MenuItem> all = items.findByMenuOrderBySortOrderAscIdAsc(menu);
        List<MenuEntry> entries = new ArrayList<>();
        for (MenuItem top : all.stream().filter(i -> i.getParent() == null).toList()) {
            List<MenuEntry> children = all.stream()
                    .filter(i -> i.getParent() == top)
                    .map(child -> entry(child, pageLink, preview, currentId, List.of()))
                    .filter(e -> e.href() != null)
                    .toList();
            MenuEntry entry = entry(top, pageLink, preview, currentId, children);
            if (entry.href() != null || entry.hasChildren()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    @Transactional
    public MenuItem add(MenuLocation menu, MenuTarget target) {
        MenuItem parent = checkParent(menu, target.parentId(), null);
        MenuItem item = target.pageId() != null
                ? MenuItem.toPage(menu, checkLabel(target.label()), page(target.pageId()), 0)
                : MenuItem.toUrl(menu, checkLabel(target.label()), checkUrl(target.url()), 0);
        item.setParent(parent);
        item.setSortOrder(nextSortOrder(menu, parent));
        items.save(item);
        audit.record(AuditAction.CREATE, "MenuItem", item.getId(), menu + ": " + item.getLabel());
        return item;
    }

    @Transactional
    public void update(long id, MenuTarget target) {
        MenuItem item = get(id);
        MenuItem parent = checkParent(item.getMenu(), target.parentId(), item);
        item.setLabel(checkLabel(target.label()));
        if (target.pageId() != null) {
            item.pointTo(page(target.pageId()));
        } else {
            item.pointTo(checkUrl(target.url()));
        }
        if (!Objects.equals(parent, item.getParent())) {
            item.setParent(parent);
            item.setSortOrder(nextSortOrder(item.getMenu(), parent));
        }
        audit.record(AuditAction.UPDATE, "MenuItem", id, item.getMenu() + ": " + item.getLabel());
    }

    /** Intercambia la posición con la entrada hermana de arriba (-1) o de abajo (+1). */
    @Transactional
    public void move(long id, int direction) {
        MenuItem item = get(id);
        List<MenuItem> siblings = items.findByMenuOrderBySortOrderAscIdAsc(item.getMenu()).stream()
                .filter(i -> i.getParent() == item.getParent())
                .toList();
        // Se renumera primero: así dos entradas con el mismo orden también se pueden mover.
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSortOrder(i);
        }
        int index = siblings.indexOf(item);
        int target = index + Integer.signum(direction);
        if (target < 0 || target >= siblings.size()) {
            return;
        }
        MenuItem other = siblings.get(target);
        other.setSortOrder(index);
        item.setSortOrder(target);
    }

    /** Borra la entrada y sus subentradas (la llave foránea las borra en cascada). */
    @Transactional
    public void delete(long id) {
        MenuItem item = get(id);
        items.findByMenuOrderBySortOrderAscIdAsc(item.getMenu()).stream()
                .filter(i -> i.getParent() == item)
                .forEach(items::delete);
        items.delete(item);
        audit.record(AuditAction.DELETE, "MenuItem", id, item.getMenu() + ": " + item.getLabel());
    }

    private MenuEntry entry(MenuItem item, Function<Page, String> pageLink, boolean preview, Long currentId,
                            List<MenuEntry> children) {
        Page page = item.getPage();
        if (page == null) {
            return new MenuEntry(item.getId(), item.getLabel(), item.getUrl(), SafeUrls.isExternal(item.getUrl()), false, children);
        }
        boolean visible = preview || page.isPublished();
        boolean current = Objects.equals(page.getId(), currentId);
        return new MenuEntry(item.getId(), item.getLabel(), visible ? pageLink.apply(page) : null, false, current, children);
    }

    private MenuItem checkParent(MenuLocation menu, Long parentId, MenuItem self) {
        if (parentId == null) {
            return null;
        }
        MenuItem parent = get(parentId);
        if (parent.getMenu() != menu) {
            throw new PageException("El submenú tiene que estar en el mismo menú");
        }
        if (parent.getParent() != null) {
            throw new PageException("Los menús tienen dos niveles como máximo");
        }
        if (self != null && (parent == self || hasChildren(self))) {
            throw new PageException("Una entrada con subentradas no puede quedar dentro de otra");
        }
        return parent;
    }

    private boolean hasChildren(MenuItem item) {
        return items.findByMenuOrderBySortOrderAscIdAsc(item.getMenu()).stream().anyMatch(i -> i.getParent() == item);
    }

    private int nextSortOrder(MenuLocation menu, MenuItem parent) {
        return items.findByMenuOrderBySortOrderAscIdAsc(menu).stream()
                .filter(i -> i.getParent() == parent)
                .mapToInt(MenuItem::getSortOrder)
                .max().orElse(-1) + 1;
    }

    private Page page(long pageId) {
        return pages.findById(pageId).orElseThrow(() -> new PageException("La página elegida no existe"));
    }

    private static String checkLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new PageException("La entrada necesita un texto");
        }
        if (label.strip().length() > MAX_LABEL) {
            throw new PageException("El texto del menú admite hasta " + MAX_LABEL + " caracteres");
        }
        return label.strip();
    }

    private static String checkUrl(String url) {
        if (!SafeUrls.isAllowed(url)) {
            throw new PageException("Elige una página o escribe un enlace válido (/ruta o https://…)");
        }
        return url.strip();
    }

    /** Lo que se elige al crear o editar una entrada: una página propia o un enlace, nunca ambos. */
    public record MenuTarget(String label, Long pageId, String url, Long parentId) {
    }
}
