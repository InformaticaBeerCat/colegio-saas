package cl.colegiosaas.page;

import java.util.List;

/**
 * Entrada de menú lista para mostrar: el enlace ya resuelto y sus hijos.
 *
 * @param href     nulo si la entrada solo agrupa un submenú (su página no está publicada)
 * @param external abre otro sitio: se marca para lectores de pantalla
 * @param current  es la página que se está viendo ({@code aria-current="page"})
 */
public record MenuEntry(long id, String label, String href, boolean external, boolean current, List<MenuEntry> children) {

    public MenuEntry {
        children = List.copyOf(children);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
