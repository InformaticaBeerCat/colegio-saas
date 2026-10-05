package cl.colegiosaas.site.web;

import cl.colegiosaas.platform.Address;
import cl.colegiosaas.site.FooterSettings;
import cl.colegiosaas.site.SocialLink;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pie de página y datos de contacto. Las redes son un campo por red: vacío = no se muestra. */
@Getter
@Setter
public class FooterForm {

    @Size(max = 500, message = "Hasta 500 caracteres")
    private String footerText;

    @Pattern(regexp = "|\\+\\d{8,15}", message = "Formato internacional, sin espacios: +56912345678")
    private String whatsappNumber;

    @Size(max = 30)
    private String phone;

    @Email(message = "Correo no válido")
    private String contactEmail;

    @Size(max = 200)
    private String street;

    @Size(max = 80)
    private String commune;

    @Size(max = 80)
    private String region;

    private Map<SocialLink.Network, String> social = new EnumMap<>(SocialLink.Network.class);

    static FooterForm of(FooterSettings footer) {
        FooterForm form = new FooterForm();
        form.footerText = footer.footerText();
        form.whatsappNumber = footer.whatsappNumber();
        form.phone = footer.phone();
        form.contactEmail = footer.contactEmail();
        if (footer.address() != null) {
            form.street = footer.address().street();
            form.commune = footer.address().commune();
            form.region = footer.address().region();
        }
        footer.socialLinks().forEach(link -> form.social.put(link.network(), link.url()));
        return form;
    }

    /** Conserva las coordenadas ya guardadas: el formulario solo edita el texto de la dirección. */
    FooterSettings toSettings(Address previous) {
        List<SocialLink> links = new ArrayList<>();
        for (SocialLink.Network network : SocialLink.Network.values()) {
            String url = social.get(network);
            if (url != null && !url.isBlank()) {
                links.add(new SocialLink(network, url.strip()));
            }
        }
        boolean noAddress = isBlank(street) && isBlank(commune) && isBlank(region);
        Address address = noAddress ? null : new Address(blank(street), blank(commune), blank(region),
                previous == null ? null : previous.latitude(), previous == null ? null : previous.longitude());
        return new FooterSettings(footerText, whatsappNumber, links, phone, contactEmail, address);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blank(String value) {
        return isBlank(value) ? null : value.strip();
    }
}
