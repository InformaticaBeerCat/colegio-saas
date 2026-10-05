package cl.colegiosaas.privacy;

import java.util.Objects;

/** Persona titular de los datos que llena un formulario (apoderado, postulante, visitante). */
public record DataSubject(String name, String email) {

    public DataSubject {
        Objects.requireNonNull(email, "email");
    }
}
