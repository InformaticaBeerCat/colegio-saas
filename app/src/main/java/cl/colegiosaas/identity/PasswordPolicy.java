package cl.colegiosaas.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Política de contraseñas: largo por sobre complejidad (recomendación NIST 800-63B). Una frase
 * larga y fácil de recordar es más segura que "P@ssw0rd!" y no obliga a anotarla.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final List<String> COMMON = List.of(
            "123456789012", "contraseña123", "password1234", "qwertyuiopas", "colegio12345", "administrador");

    private PasswordPolicy() {
    }

    /** Lista de problemas; vacía si la contraseña es aceptable. */
    public static List<String> problems(String password, String email) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("Debe tener al menos " + MIN_LENGTH + " caracteres. Una frase corta sirve.");
            return problems;
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("No puede superar " + MAX_LENGTH + " caracteres.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (email != null) {
            String localPart = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (localPart.length() >= 3 && lower.contains(localPart)) {
                problems.add("No debe contener tu correo.");
            }
        }
        if (password.chars().distinct().count() <= 2) {
            problems.add("No puede ser uno o dos caracteres repetidos.");
        }
        if (COMMON.contains(lower)) {
            problems.add("Es una contraseña demasiado común.");
        }
        return problems;
    }
}
