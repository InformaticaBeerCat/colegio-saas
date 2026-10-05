package cl.colegiosaas.media;

import java.util.List;

/**
 * Revisa los textos que acompañan a una foto pública (pie, texto alternativo). La implementa el módulo de
 * autorizaciones (MED-12: sin nombres completos de estudiantes junto a fotos); así medios no depende de
 * estudiantes.
 */
public interface PublicTextPolicy {

    /** Problemas encontrados, en frases para mostrar; vacío si el texto se puede publicar. */
    List<String> problems(String text);
}
