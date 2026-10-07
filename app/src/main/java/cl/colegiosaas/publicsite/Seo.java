package cl.colegiosaas.publicsite;

/**
 * Metadatos para buscadores y redes sociales de una página pública (SEO-01, SEO-03). Las plantillas lo
 * reciben como {@code seo}.
 *
 * @param canonicalUrl URL absoluta y única de la página (sin parámetros de campaña)
 * @param type         tipo Open Graph: {@code website} o {@code article}
 * @param imageUrl     imagen para compartir; nula = sin imagen
 * @param jsonLd       datos estructurados schema.org ya serializados; nulo = sin datos
 */
public record Seo(String canonicalUrl, String type, String imageUrl, String jsonLd) {

    public Seo withJsonLd(String json) {
        return new Seo(canonicalUrl, type, imageUrl, json);
    }

    public Seo withImage(String url) {
        return url == null ? this : new Seo(canonicalUrl, type, url, jsonLd);
    }

    public Seo asArticle() {
        return new Seo(canonicalUrl, "article", imageUrl, jsonLd);
    }
}
