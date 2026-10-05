package cl.colegiosaas.shared.web;

/**
 * Regla de negocio que el usuario puede corregir (contenido, documentos, calendario…). El panel
 * muestra el mensaje tal cual.
 */
public class RuleViolation extends RuntimeException {

    public RuleViolation(String message) {
        super(message);
    }
}
