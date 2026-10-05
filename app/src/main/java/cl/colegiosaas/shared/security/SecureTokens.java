package cl.colegiosaas.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Tokens y códigos aleatorios para enlaces públicos (cancelar cita, ver estado de una solicitud…). */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Sin 0/O ni 1/I/L: los códigos se dictan por teléfono. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private SecureTokens() {
    }

    /** Token para enlaces (256 bits). Se envía al usuario; en la base se guarda solo su {@link #hash}. */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hex. Basta sin llave porque el token ya es aleatorio y largo. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Código legible para personas, p. ej. "C-7KQ2M9XA" (número de ticket, COM-01). */
    public static String newCode(String prefix, int length) {
        StringBuilder code = new StringBuilder(prefix);
        for (int i = 0; i < length; i++) {
            code.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }
}
