package cl.colegiosaas.media;

/**
 * Zona a difuminar en una imagen (MED-07), en fracciones del ancho y alto (0 a 1):
 * así sirve para la imagen original y para todas sus versiones redimensionadas.
 */
public record BlurRegion(double x, double y, double width, double height) {

    public BlurRegion {
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 1 || y + height > 1) {
            throw new IllegalArgumentException("La zona debe quedar dentro de la imagen (valores entre 0 y 1)");
        }
    }
}
