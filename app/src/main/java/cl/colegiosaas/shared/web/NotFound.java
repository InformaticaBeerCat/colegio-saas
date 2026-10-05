package cl.colegiosaas.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** El recurso pedido no existe (o no es visible para quien lo pide): 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFound extends RuleViolation {

    public NotFound(String message) {
        super(message);
    }
}
