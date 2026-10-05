package cl.colegiosaas.site;

import cl.colegiosaas.site.SiteDesign.ColorScheme;
import cl.colegiosaas.site.SiteDesign.Palette;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeTest {

    @Test
    void everyVariantOfEveryThemePassesTheAccessibilityReview() {
        for (Theme theme : Theme.values()) {
            for (Theme.Variant variant : theme.variants()) {
                DesignReview review = DesignReview.of(theme.design(variant.id()));
                assertThat(review.passes())
                        .as("%s/%s: %s", theme.id(), variant.id(), review.messages())
                        .isTrue();
            }
        }
    }

    @Test
    void everyCatalogFontIsBundledWithItsLicense() {
        for (FontCatalog font : FontCatalog.values()) {
            for (FontCatalog.FontFile file : font.files()) {
                assertThat(getClass().getResource("/static/fonts/" + file.fileName()))
                        .as(file.fileName()).isNotNull();
            }
            assertThat(font.license()).isIn("OFL-1.1", "Apache-2.0");
        }
    }

    @Test
    void defaultDesignIsTheInstitutionalTheme() {
        assertThat(SiteDesign.defaults().theme()).isEqualTo("classic");
        assertThat(SiteDesign.defaults().variant()).isEqualTo("navy");
        assertThat(DesignReview.of(SiteDesign.defaults()).passes()).isTrue();
    }

    @Test
    void lowContrastPalettesAndUnknownFontsAreRejected() {
        SiteDesign base = SiteDesign.defaults();
        Palette washedOut = new Palette(HexColor.of("#9EC5FE"), base.palette().secondary(), base.palette().accent(),
                HexColor.of("#FFFFFF"), HexColor.of("#F4F5F7"), HexColor.of("#999999"));
        SiteDesign design = new SiteDesign(base.theme(), base.variant(), washedOut,
                new SiteDesign.Typography("Comic Sans MS", "Inter"), base.cornerRadius(), base.shadow(), base.colorScheme());

        DesignReview review = DesignReview.of(design);

        assertThat(review.passes()).isFalse();
        assertThat(review.failures()).extracting(DesignReview.ContrastCheck::label)
                .contains("Texto sobre el fondo", "Enlaces y títulos (primario) sobre el fondo");
        assertThat(review.messages()).anyMatch(m -> m.contains("Comic Sans MS"));
        assertThat(review.messages()).anyMatch(m -> m.contains("el mínimo es 4,50:1"));
    }

    @Test
    void stylesheetCarriesTokensAndOnlyTheFontsInUse() {
        ThemeStylesheet css = ThemeStylesheet.of(Theme.CLASSIC.design("navy"));

        assertThat(css.css())
                .contains("--color-primary: #1F3A5F;")
                .contains("--color-on-primary: #FFFFFF;")
                .contains("font-family: \"Merriweather\";")
                .contains("url(\"../fonts/source-sans-3.woff2\")")
                .doesNotContain("nunito")
                .doesNotContain("prefers-color-scheme");
        assertThat(css.version()).hasSize(12);
        // Mismo diseño, misma huella: la URL del CSS solo cambia cuando cambia el diseño.
        assertThat(ThemeStylesheet.of(Theme.CLASSIC.design("navy")).version()).isEqualTo(css.version());
        assertThat(ThemeStylesheet.of(Theme.CLASSIC.design("forest")).version()).isNotEqualTo(css.version());
    }

    @Test
    void automaticSchemeAddsADarkVersionThatStillPassesAa() {
        SiteDesign light = Theme.MODERN.design("graphite");
        SiteDesign auto = new SiteDesign(light.theme(), light.variant(), light.palette(), light.typography(),
                light.cornerRadius(), light.shadow(), ColorScheme.AUTO);

        assertThat(ThemeStylesheet.of(auto).css()).contains("@media (prefers-color-scheme: dark)");
        Palette dark = ThemeStylesheet.darkVersion(light.palette());
        assertThat(Contrast.ratio(dark.primary(), dark.surface())).isGreaterThanOrEqualTo(Contrast.AA_TEXT);
        assertThat(DesignReview.of(new SiteDesign(light.theme(), light.variant(), dark, light.typography(),
                light.cornerRadius(), light.shadow(), ColorScheme.DARK)).passes()).isTrue();
    }
}
