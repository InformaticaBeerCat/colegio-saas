package cl.colegiosaas.site;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diseño del sitio (CFG-02, CFG-03, CFG-08): se edita un borrador, se previsualiza y recién al publicar
 * lo ven los visitantes. Ningún diseño que no pase {@link DesignReview} llega a guardarse.
 */
@Service
public class SiteDesignService {

    private final SiteSettingsRepository settings;
    private final AuditTrail audit;

    SiteDesignService(SiteSettingsRepository settings, AuditTrail audit) {
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SiteDesign published() {
        return settings.findSingleton().map(SiteSettings::getPublishedDesign).orElseGet(SiteDesign::defaults);
    }

    @Transactional(readOnly = true)
    public SiteDesign draft() {
        return settings.findSingleton().map(SiteSettings::getDraftDesign).orElseGet(SiteDesign::defaults);
    }

    @Transactional(readOnly = true)
    public boolean hasUnpublishedChanges() {
        return settings.findSingleton().map(SiteSettings::hasUnpublishedChanges).orElse(false);
    }

    /** Guarda el borrador si pasa la revisión; si no, lanza {@link DesignRejectedException} con el detalle. */
    @Transactional
    public void saveDraft(SiteDesign design) {
        DesignReview review = DesignReview.of(design);
        if (!review.passes()) {
            throw new DesignRejectedException(review);
        }
        SiteSettings current = load();
        if (!design.equals(current.getDraftDesign())) {
            current.updateDraft(design);
            audit.record(AuditAction.UPDATE, "SiteDesign", current.getId(), "Borrador: tema " + design.theme() + "/" + design.variant());
        }
    }

    /** Reemplaza el borrador por un tema base completo (colores, fuentes, radios). */
    @Transactional
    public void applyTheme(Theme theme, String variantId) {
        saveDraft(theme.design(variantId));
    }

    @Transactional
    public void publish() {
        SiteSettings current = load();
        DesignReview review = DesignReview.of(current.getDraftDesign());
        if (!review.passes()) {
            throw new DesignRejectedException(review);
        }
        current.publishDraft();
        audit.record(AuditAction.PUBLISH, "SiteDesign", current.getId(),
                "Tema " + current.getPublishedDesign().theme() + "/" + current.getPublishedDesign().variant());
    }

    @Transactional
    public void discardDraft() {
        load().discardDraft();
    }

    private SiteSettings load() {
        return settings.findSingleton().orElseThrow(() -> new IllegalStateException("El sitio no está instalado"));
    }
}
