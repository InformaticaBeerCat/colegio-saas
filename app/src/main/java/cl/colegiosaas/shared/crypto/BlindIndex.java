package cl.colegiosaas.shared.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Índice ciego: HMAC-SHA256 de un valor normalizado. Permite buscar "todas las filas de
 * ana@mail.cl" (p. ej., para ejercer derechos, PRV-07) sin guardar el email en claro.
 * Las entidades lo reciben en su constructor y calculan el hash ellas mismas.
 */
public class BlindIndex {

    private final SecretKeySpec key;

    public BlindIndex(byte[] key) {
        this.key = new SecretKeySpec(key, "HmacSHA256");
    }

    public String of(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(normalize(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el índice", e);
        }
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
