package cl.colegiosaas.media;

/** Antivirus de los archivos subidos (SEG-03), antes de guardarlos. */
public interface FileScanner {

    Verdict scan(byte[] content);

    /** Si el escaneo es real; sin antivirus el sistema lo dice en el log y en el panel. */
    boolean isActive();

    /**
     * @param clean     sin amenazas detectadas
     * @param signature nombre de la amenaza encontrada; nulo si está limpio
     */
    record Verdict(boolean clean, String signature) {

        public static Verdict ok() {
            return new Verdict(true, null);
        }

        public static Verdict infected(String signature) {
            return new Verdict(false, signature);
        }
    }
}
