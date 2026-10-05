package cl.colegiosaas.calendar;

public enum RegistrationStatus {
    CONFIRMED,
    /** Sin cupo: espera que alguien cancele (EVE-02). */
    WAITLISTED,
    CANCELLED,
    ATTENDED
}
