package cl.colegiosaas.consent;

import cl.colegiosaas.media.PublicTextPolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * MED-12: junto a una foto pública no puede ir el nombre completo de un estudiante (nombre y apellido).
 * El nombre de pila solo o el curso sí. Los nombres están cifrados en la base, así que la comparación
 * se hace en memoria, sin tildes ni mayúsculas.
 */
@Component
class StudentNamePolicy implements PublicTextPolicy {

    private final StudentRepository students;

    StudentNamePolicy(StudentRepository students) {
        this.students = students;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> problems(String text) {
        String haystack = " " + normalize(text) + " ";
        boolean found = students.findByActiveTrue().stream()
                .anyMatch(student -> fullNameForms(student.getFullName()).stream().anyMatch(form -> haystack.contains(" " + form + " ")));
        return found
                ? List.of("El texto incluye el nombre y apellido de un estudiante: junto a fotos públicas usa solo el nombre de pila o el curso (MED-12).")
                : List.of();
    }

    /**
     * Formas en que aparece un nombre completo: cualquier nombre de pila seguido de cualquier apellido.
     * "María José Pérez Soto" → "maria perez", "maria soto", "jose perez", "maria jose perez"…
     * Se asume que los dos últimos términos son los apellidos (uso chileno).
     */
    static List<String> fullNameForms(String fullName) {
        String[] words = normalize(fullName).split(" ");
        if (words.length < 2) {
            return List.of();
        }
        int surnamesFrom = words.length >= 3 ? words.length - 2 : 1;
        String[] given = Arrays.copyOfRange(words, 0, surnamesFrom);
        String[] surnames = Arrays.copyOfRange(words, surnamesFrom, words.length);
        List<String> forms = new ArrayList<>();
        forms.add(String.join(" ", words));
        forms.add(String.join(" ", given) + " " + surnames[0]);
        for (String name : given) {
            for (String surname : surnames) {
                forms.add(name + " " + surname);
            }
        }
        return forms;
    }

    static String normalize(String text) {
        String ascii = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ]+", " ").strip();
    }
}
