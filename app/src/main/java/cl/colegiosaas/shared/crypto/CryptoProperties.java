package cl.colegiosaas.shared.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Llaves de cifrado de datos personales (SEG-04), en Base64 de 32 bytes cada una.
 * Cada instalación tiene las suyas; se entregan por variable de entorno, nunca en el código.
 *
 * @param fieldKey llave AES-256 para cifrar columnas
 * @param indexKey llave HMAC para los índices ciegos (buscar por email sin descifrar)
 */
@ConfigurationProperties("app.crypto")
public record CryptoProperties(String fieldKey, String indexKey) {
}
