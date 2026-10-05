package cl.colegiosaas.site;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.media.MediaUrls;
import cl.colegiosaas.media.ResponsiveImage;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.shared.persistence.SingletonEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Logo y favicon del colegio. Se suben como fotos de la biblioteca ya exentas de revisión: no muestran
 * personas y las sube quien administra el diseño (como hace el asistente de primer arranque).
 */
@Service
public class BrandService {

    private final SiteSettingsRepository settings;
    private final SchoolRepository schools;
    private final MediaLibrary media;
    private final MediaUrls urls;
    private final AuditTrail audit;

    BrandService(SiteSettingsRepository settings, SchoolRepository schools, MediaLibrary media, MediaUrls urls, AuditTrail audit) {
        this.settings = settings;
        this.schools = schools;
        this.media = media;
        this.urls = urls;
        this.audit = audit;
    }

    /** Logo listo para el encabezado y dirección del favicon; nulos si no hay. */
    public record Brand(ResponsiveImage logo, String faviconHref) {
    }

    @Transactional(readOnly = true)
    public Brand current() {
        SiteSettings site = settings.findWithBrandById(SingletonEntity.ID).orElse(null);
        if (site == null) {
            return new Brand(null, null);
        }
        return new Brand(urls.picture(site.getLogo()), site.getFavicon() == null ? null : urls.src(site.getFavicon(), 64));
    }

    @Transactional
    public void uploadLogo(MediaLibrary.Upload upload, long userId) {
        MediaAsset logo = media.uploadImage(upload, userId, null, Set.of("marca"), true);
        String name = schools.findSingleton().map(School::getName).orElse("Colegio");
        media.updateAltText(logo.getId(), "Logo de " + name);
        site().setLogo(logo);
        audit.record(AuditAction.UPDATE, "SiteSettings", SingletonEntity.ID, "Logo");
    }

    @Transactional
    public void uploadFavicon(MediaLibrary.Upload upload, long userId) {
        MediaAsset favicon = media.uploadImage(upload, userId, null, Set.of("marca"), true);
        media.updateAltText(favicon.getId(), "Ícono del sitio");
        site().setFavicon(favicon);
        audit.record(AuditAction.UPDATE, "SiteSettings", SingletonEntity.ID, "Favicon");
    }

    @Transactional
    public void removeLogo() {
        site().setLogo(null);
    }

    @Transactional
    public void removeFavicon() {
        site().setFavicon(null);
    }

    private SiteSettings site() {
        return settings.findById(SingletonEntity.ID).orElseThrow(() -> new IllegalStateException("El sitio no está instalado"));
    }
}
