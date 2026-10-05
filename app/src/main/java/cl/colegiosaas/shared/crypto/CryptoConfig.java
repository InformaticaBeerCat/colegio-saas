package cl.colegiosaas.shared.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

/**
 * Pública para que los tests de repositorio la importen: Hibernate necesita {@link FieldCipher}
 * para crear {@link EncryptedStringConverter} al arrancar.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {

    @Bean
    FieldCipher fieldCipher(CryptoProperties properties) {
        return new FieldCipher(decode(properties.fieldKey(), "app.crypto.field-key"));
    }

    @Bean
    BlindIndex blindIndex(CryptoProperties properties) {
        return new BlindIndex(decode(properties.indexKey(), "app.crypto.index-key"));
    }

    private static byte[] decode(String base64, String property) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("Falta la llave " + property);
        }
        byte[] key = Base64.getDecoder().decode(base64);
        if (key.length != 32) {
            throw new IllegalStateException(property + " debe tener 32 bytes (Base64 de 44 caracteres)");
        }
        return key;
    }
}
