package cl.colegiosaas.page;

/** Regla de páginas o menús que el usuario puede corregir; el mensaje se le muestra tal cual. */
public class PageException extends RuntimeException {

    public PageException(String message) {
        super(message);
    }
}
