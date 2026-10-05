package cl.colegiosaas.media;

/**
 * Datos para un {@code <picture>}: WebP para los navegadores que lo entienden y JPEG/PNG de respaldo,
 * cada uno con sus anchos (MED-02). Ancho y alto evitan que la página salte al cargar (UX-02).
 */
public record ResponsiveImage(String alt, int width, int height, String webpSrcset, String fallbackSrcset, String src) {
}
