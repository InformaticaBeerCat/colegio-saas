package cl.colegiosaas.media;

/** Archivo rechazado al subir (tipo, tamaño, vacío); el mensaje se muestra tal cual. */
public class FileUploadException extends RuntimeException {

    public FileUploadException(String message) {
        super(message);
    }
}
