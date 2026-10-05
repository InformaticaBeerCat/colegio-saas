package cl.colegiosaas.site.web;

import cl.colegiosaas.site.HexColor;
import cl.colegiosaas.site.SiteDesign;
import cl.colegiosaas.site.SiteDesign.ColorScheme;
import cl.colegiosaas.site.SiteDesign.CornerRadius;
import cl.colegiosaas.site.SiteDesign.Palette;
import cl.colegiosaas.site.SiteDesign.Shadow;
import cl.colegiosaas.site.SiteDesign.Typography;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Tokens editables del diseño (CFG-03). El tema y la variante se cambian aparte, con "usar este tema". */
@Getter
@Setter
public class DesignForm {

    private static final String HEX = "#[0-9a-fA-F]{6}";
    private static final String HEX_MESSAGE = "Usa el formato #RRGGBB";

    @NotBlank
    private String theme;

    @NotBlank
    private String variant;

    @NotNull @Pattern(regexp = HEX, message = HEX_MESSAGE)
    private String primary;
    @NotNull @Pattern(regexp = HEX, message = HEX_MESSAGE)
    private String secondary;
    @NotNull @Pattern(regexp = HEX, message = HEX_MESSAGE)
    private String accent;
    @NotNull @Pattern(regexp = HEX, message = HEX_MESSAGE)
    private String background;
    @NotNull @Pattern(regexp = HEX, message = HEX_MESSAGE)
    private String surface;
    @NotNull @Pattern(regexp = HEX, message = HEX_MESSAGE)
    private String text;

    @NotBlank
    private String headingFont;

    @NotBlank
    private String bodyFont;

    @NotNull
    private CornerRadius cornerRadius;

    @NotNull
    private Shadow shadow;

    @NotNull
    private ColorScheme colorScheme;

    public static DesignForm of(SiteDesign design) {
        DesignForm form = new DesignForm();
        form.theme = design.theme();
        form.variant = design.variant();
        Palette p = design.palette();
        form.primary = p.primary().lowercase();
        form.secondary = p.secondary().lowercase();
        form.accent = p.accent().lowercase();
        form.background = p.background().lowercase();
        form.surface = p.surface().lowercase();
        form.text = p.text().lowercase();
        form.headingFont = design.typography().headingFont();
        form.bodyFont = design.typography().bodyFont();
        form.cornerRadius = design.cornerRadius();
        form.shadow = design.shadow();
        form.colorScheme = design.colorScheme();
        return form;
    }

    SiteDesign toDesign() {
        return new SiteDesign(theme, variant,
                new Palette(HexColor.of(primary), HexColor.of(secondary), HexColor.of(accent),
                        HexColor.of(background), HexColor.of(surface), HexColor.of(text)),
                new Typography(headingFont, bodyFont),
                cornerRadius, shadow, colorScheme);
    }
}
