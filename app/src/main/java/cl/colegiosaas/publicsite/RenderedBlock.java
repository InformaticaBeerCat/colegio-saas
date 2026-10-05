package cl.colegiosaas.publicsite;

import cl.colegiosaas.page.Block;

/**
 * Bloque listo para la plantilla: su tipo ({@code hero}, {@code latest-news}…), el bloque tal como se
 * guardó y los datos que hubo que buscar (noticias, eventos, preguntas). {@code data} es nulo en los
 * bloques que se muestran solo con lo que traen.
 */
public record RenderedBlock(String type, Block block, Object data) {
}
