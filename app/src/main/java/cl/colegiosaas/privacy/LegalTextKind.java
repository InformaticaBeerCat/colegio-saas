package cl.colegiosaas.privacy;

import java.util.Arrays;
import java.util.Optional;

/**
 * Textos legales versionados (DOC-06, PRV-01). Cada formulario tiene su propio aviso de tratamiento
 * (finalidad, responsable, plazo de conservación, derechos).
 */
public enum LegalTextKind {
    PRIVACY_POLICY("politica"),
    COOKIE_POLICY("cookies"),
    TERMS("terminos"),
    /** Formulario de autorización de uso de imagen que firman los apoderados. */
    IMAGE_CONSENT_FORM("autorizacion-imagen"),
    NOTICE_CONTACT("aviso-contacto"),
    NOTICE_SCHEDULING("aviso-agenda"),
    NOTICE_EVENTS("aviso-eventos"),
    NOTICE_ADMISSIONS("aviso-admision"),
    NOTICE_NEWSLETTER("aviso-boletin"),
    NOTICE_DATA_REQUESTS("aviso-derechos");

    private final String slug;

    LegalTextKind(String slug) {
        this.slug = slug;
    }

    /** Parte de la URL pública: {@code /privacidad/<slug>}. No se cambia: hay enlaces guardados. */
    public String slug() {
        return slug;
    }

    public boolean isNotice() {
        return name().startsWith("NOTICE_");
    }

    public static Optional<LegalTextKind> bySlug(String slug) {
        return Arrays.stream(values()).filter(k -> k.slug.equals(slug)).findFirst();
    }
}
