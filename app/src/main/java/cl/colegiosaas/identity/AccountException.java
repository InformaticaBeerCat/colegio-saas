package cl.colegiosaas.identity;

/** Error de negocio con un mensaje apto para mostrar tal cual en pantalla. */
public class AccountException extends RuntimeException {

    public AccountException(String message) {
        super(message);
    }
}
