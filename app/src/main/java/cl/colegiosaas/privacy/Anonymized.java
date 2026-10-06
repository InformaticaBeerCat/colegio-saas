package cl.colegiosaas.privacy;

/**
 * Valores con que se reemplazan los datos de una persona al anonimizar (PRV-06, PRV-07). Las columnas de
 * email son obligatorias, así que se deja un marcador que no corresponde a nadie ni calza con ningún índice.
 */
public final class Anonymized {

    public static final String EMAIL = "anonimizado@invalid";
    /** No es un HMAC válido (tiene letras fuera de hex), así nunca coincide con una búsqueda por email. */
    public static final String EMAIL_HASH = "anonimizado";
    public static final String TEXT = "[anonimizado]";

    private Anonymized() {
    }

    public static boolean isAnonymized(String emailHash) {
        return EMAIL_HASH.equals(emailHash);
    }
}
