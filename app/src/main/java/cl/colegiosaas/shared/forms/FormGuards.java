package cl.colegiosaas.shared.forms;

/** Mensajes para la persona cuando el antispam frena un envío legítimo. */
public final class FormGuards {

    private FormGuards() {
    }

    public static String message(FormGuard.Verdict verdict) {
        return switch (verdict) {
            case EXPIRED -> "El formulario estuvo abierto mucho tiempo. Revisa los datos y vuelve a enviarlo.";
            case TOO_MANY -> "Recibimos varios envíos seguidos desde tu conexión. Espera unos minutos e inténtalo de nuevo.";
            default -> "No pudimos recibir el formulario. Inténtalo de nuevo.";
        };
    }
}
