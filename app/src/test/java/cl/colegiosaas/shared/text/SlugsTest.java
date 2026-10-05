package cl.colegiosaas.shared.text;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void removesAccentsAndSymbols() {
        assertThat(Slugs.slugify("Reunión de Apoderados 1° A")).isEqualTo("reunion-de-apoderados-1-a");
        assertThat(Slugs.slugify("¡Ñandú!")).isEqualTo("nandu");
        assertThat(Slugs.slugify(null)).isEmpty();
    }

    @Test
    void uniqueAddsASuffixAndFallsBackWhenEmpty() {
        Set<String> taken = Set.of("acto", "acto-2");
        assertThat(Slugs.unique("Acto", "x", taken::contains)).isEqualTo("acto-3");
        assertThat(Slugs.unique("¡!", "noticia", taken::contains)).isEqualTo("noticia");
    }
}
