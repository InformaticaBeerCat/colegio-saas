package cl.colegiosaas.site;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.shared.persistence.SingletonEntity;
import cl.colegiosaas.shared.web.SafeUrls;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pie de página y datos de contacto públicos del colegio (CFG-05, PUB-04). */
@Service
public class FooterService {

    private final SiteSettingsRepository settings;
    private final SchoolRepository schools;
    private final AuditTrail audit;

    FooterService(SiteSettingsRepository settings, SchoolRepository schools, AuditTrail audit) {
        this.settings = settings;
        this.schools = schools;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public FooterSettings current() {
        SiteSettings site = settings.findSingleton().orElse(null);
        School school = schools.findSingleton().orElse(null);
        return new FooterSettings(
                site == null ? null : site.getFooterText(),
                site == null ? null : site.getWhatsappNumber(),
                site == null ? null : site.getSocialLinks(),
                school == null ? null : school.getPhone(),
                school == null ? null : school.getContactEmail(),
                school == null ? null : school.getAddress());
    }

    @Transactional
    public void update(FooterSettings footer) {
        footer.socialLinks().forEach(link -> {
            if (!SafeUrls.isExternal(link.url()) || !SafeUrls.isAllowed(link.url())) {
                throw new IllegalArgumentException("El enlace de " + link.network() + " debe empezar con https://");
            }
        });
        SiteSettings site = settings.findById(SingletonEntity.ID).orElseThrow();
        site.setFooterText(blankToNull(footer.footerText()));
        site.setWhatsappNumber(blankToNull(footer.whatsappNumber()));
        site.replaceSocialLinks(footer.socialLinks());

        School school = schools.findById(SingletonEntity.ID).orElseThrow();
        school.setPhone(blankToNull(footer.phone()));
        school.setContactEmail(blankToNull(footer.contactEmail()));
        school.setAddress(footer.address());
        audit.record(AuditAction.UPDATE, "SiteSettings", site.getId(), "Pie de página y datos de contacto");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
