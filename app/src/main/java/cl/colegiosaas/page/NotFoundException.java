package cl.colegiosaas.page;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** La página, el bloque o la entrada de menú no existe (o ya se borró): el panel responde 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends PageException {

    public NotFoundException(String message) {
        super(message);
    }
}
