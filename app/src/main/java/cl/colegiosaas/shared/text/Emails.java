package cl.colegiosaas.shared.text;

import java.util.regex.Pattern;

/** Validación de emails escritos en formularios públicos: forma básica y largo, sin pretender cubrir el RFC. */
public final class Emails {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Emails() {
    }

    public static boolean isValid(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email).matches();
    }
}
