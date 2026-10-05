package cl.colegiosaas.site;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ContrastTest {

    @Test
    void ratiosMatchWcagReferenceValues() {
        assertThat(Contrast.ratio(HexColor.BLACK, HexColor.WHITE)).isCloseTo(21.0, within(0.001));
        assertThat(Contrast.ratio(HexColor.WHITE, HexColor.WHITE)).isCloseTo(1.0, within(0.001));
        // #767676 es el gris más claro que cumple AA sobre blanco; #777777 ya no.
        assertThat(Contrast.ratio(HexColor.of("#767676"), HexColor.WHITE)).isGreaterThanOrEqualTo(Contrast.AA_TEXT);
        assertThat(Contrast.ratio(HexColor.of("#777777"), HexColor.WHITE)).isLessThan(Contrast.AA_TEXT);
        // Es simétrico: no importa cuál es el texto y cuál el fondo.
        assertThat(Contrast.ratio(HexColor.of("#1F3A5F"), HexColor.of("#F5F3EE")))
                .isEqualTo(Contrast.ratio(HexColor.of("#F5F3EE"), HexColor.of("#1F3A5F")));
    }

    @Test
    void readableTextIsWhiteOnDarkAndBlackOnLightColors() {
        assertThat(Contrast.readableOn(HexColor.of("#1F3A5F"))).isEqualTo(HexColor.WHITE);
        assertThat(Contrast.readableOn(HexColor.of("#FCD34D"))).isEqualTo(HexColor.BLACK);
    }

    @Test
    void adjustingKeepsTheHueUntilReachingTheRequiredContrast() {
        HexColor dark = HexColor.of("#121417");
        HexColor navy = HexColor.of("#1F3A5F");

        HexColor adjusted = Contrast.adjustToContrast(navy, dark, Contrast.AA_TEXT);

        assertThat(Contrast.ratio(adjusted, dark)).isGreaterThanOrEqualTo(Contrast.AA_TEXT);
        assertThat(adjusted).isNotEqualTo(HexColor.WHITE);
        // Un color que ya cumple no se toca.
        assertThat(Contrast.adjustToContrast(HexColor.WHITE, dark, Contrast.AA_TEXT)).isEqualTo(HexColor.WHITE);
    }

    @Test
    void hexColorsExposeChannelsAndMix() {
        HexColor color = HexColor.of("#1f3a5f");
        assertThat(color.red()).isEqualTo(0x1F);
        assertThat(color.green()).isEqualTo(0x3A);
        assertThat(color.blue()).isEqualTo(0x5F);
        assertThat(HexColor.BLACK.mix(HexColor.WHITE, 0.5)).isEqualTo(HexColor.of("#808080"));
        assertThat(color.lowercase()).isEqualTo("#1f3a5f");
    }
}
