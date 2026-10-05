package cl.colegiosaas.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Códigos TOTP (RFC 6238) compatibles con Google Authenticator, Microsoft Authenticator, 1Password…:
 * HMAC-SHA1, intervalos de 30 segundos, 6 dígitos.
 */
public final class Totp {

    public static final int PERIOD_SECONDS = 30;
    public static final int DIGITS = 6;
    /** Acepta el intervalo anterior y el siguiente: tolera relojes de teléfono algo desfasados. */
    private static final int WINDOW = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Secreto nuevo de 160 bits, en Base32 (lo que se escribe a mano o va dentro del QR). */
    public static String newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return Base32.encode(secret);
    }

    public static long stepAt(Instant instant) {
        return instant.getEpochSecond() / PERIOD_SECONDS;
    }

    public static String codeAt(String base32Secret, long step) {
        return code(Base32.decode(base32Secret), step);
    }

    /** Si el código calza con algún intervalo de la ventana, devuelve cuál (para impedir que se reutilice). */
    public static OptionalLong matchingStep(String base32Secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = Base32.decode(base32Secret);
        long current = stepAt(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            byte[] expected = code(key, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** URI que leen las apps autenticadoras desde el QR. */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            // "Truncamiento dinámico" del RFC 4226: 4 bytes desde la posición que indica el último nibble.
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
