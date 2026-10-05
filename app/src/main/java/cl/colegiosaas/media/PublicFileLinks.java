package cl.colegiosaas.media;

import org.springframework.stereotype.Component;

/**
 * Enlace público a un PDF: {@code /archivos/<sha256>/<nombre>}. La huella no se adivina y no expone ids;
 * el controlador igual verifica que el archivo pertenezca a contenido publicado antes de entregarlo.
 */
@Component("fileLinks")
public class PublicFileLinks {

    public static final String PREFIX = "/archivos/";

    public static String href(StoredFile file) {
        return PREFIX + file.getSha256() + "/" + file.getOriginalName().replace(' ', '-');
    }

    /** Para las plantillas: {@code ${@fileLinks.of(archivo)}}. */
    public String of(StoredFile file) {
        return href(file);
    }
}
