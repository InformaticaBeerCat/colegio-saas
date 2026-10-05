package cl.colegiosaas.setup;

import cl.colegiosaas.shared.security.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Token que pide el instalador. Sin él, cualquiera que llegue primero a {@code /setup} se quedaría con
 * el sitio. Se toma de {@code APP_SETUP_TOKEN} (instalación automatizada) o se genera al arrancar y
 * se muestra en el log, como hace Jenkins con su contraseña inicial.
 */
@Component
public class SetupToken {

    private final String value;

    SetupToken(@Value("${app.setup-token:}") String configured) {
        this.value = configured.isBlank() ? SecureTokens.newCode("", 12) : configured;
    }

    public boolean matches(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8), candidate.strip().getBytes(StandardCharsets.UTF_8));
    }

    String value() {
        return value;
    }
}
