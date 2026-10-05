package cl.colegiosaas.page;

import jakarta.persistence.Embeddable;

/** Metadatos editables para buscadores y redes (SEO-02). Nulos = se derivan del título y contenido. */
@Embeddable
public record SeoMetadata(String metaTitle, String metaDescription) {
}
