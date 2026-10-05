package cl.colegiosaas.privacy;

public enum DataSubjectRequestStatus {
    RECEIVED,
    /** Esperando que el solicitante acredite su identidad (o la del menor que representa). */
    VERIFYING_IDENTITY,
    IN_PROGRESS,
    COMPLETED,
    REJECTED;

    public boolean isOpen() {
        return this != COMPLETED && this != REJECTED;
    }
}
