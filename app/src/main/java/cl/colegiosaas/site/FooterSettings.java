package cl.colegiosaas.site;

import cl.colegiosaas.platform.Address;

import java.util.List;

/** Lo que muestra el pie del sitio (CFG-05): datos de contacto del colegio, redes y texto libre. */
public record FooterSettings(
        String footerText,
        String whatsappNumber,
        List<SocialLink> socialLinks,
        String phone,
        String contactEmail,
        Address address) {

    public FooterSettings {
        socialLinks = socialLinks == null ? List.of() : List.copyOf(socialLinks);
    }
}
