package cl.colegiosaas.publicsite;

import cl.colegiosaas.page.MenuLocation;
import cl.colegiosaas.page.MenuService;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.site.BrandService;
import cl.colegiosaas.site.FooterService;
import cl.colegiosaas.site.SiteAlertRepository;
import cl.colegiosaas.site.SiteDesign;
import cl.colegiosaas.site.SiteDesignService;
import cl.colegiosaas.site.Theme;
import cl.colegiosaas.site.ThemeStylesheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.function.Function;

/**
 * Arma el {@link SiteContext} de cada página. Son pocas consultas pequeñas (una fila de diseño, los
 * menús, alertas vigentes); si hace falta, el caché se agrega aquí en la fase 8 sin tocar las vistas.
 */
@Service
public class SiteContextService {

    /** Raíz de las páginas en vista previa: los enlaces del menú siguen dentro de la vista previa. */
    public static final String PREVIEW_ROOT = "/admin/preview";

    private final SchoolRepository schools;
    private final SiteDesignService designs;
    private final MenuService menus;
    private final FooterService footer;
    private final SiteAlertRepository alerts;
    private final BrandService brands;
    private final Clock clock;

    SiteContextService(SchoolRepository schools, SiteDesignService designs, MenuService menus, FooterService footer,
                       SiteAlertRepository alerts, BrandService brands, Clock clock) {
        this.schools = schools;
        this.designs = designs;
        this.menus = menus;
        this.footer = footer;
        this.alerts = alerts;
        this.brands = brands;
        this.clock = clock;
    }

    /** Sitio publicado, tal como lo ven los visitantes. */
    @Transactional(readOnly = true)
    public SiteContext publicSite(Long currentPageId) {
        return build(designs.published(), "/site/theme.css", false, currentPageId);
    }

    /** Borrador del diseño y de las páginas, solo para el panel (CFG-08). */
    @Transactional(readOnly = true)
    public SiteContext preview(Long currentPageId) {
        return build(designs.draft(), "/admin/preview-theme.css", true, currentPageId);
    }

    /** Enlace a una página, en el sitio o en la vista previa. La portada vive en la raíz. */
    public static String linkTo(Page page, boolean preview) {
        String root = preview ? PREVIEW_ROOT : "";
        if (page.getKind() == PageKind.HOME) {
            return preview ? PREVIEW_ROOT : "/";
        }
        return root + "/" + page.getSlug();
    }

    private SiteContext build(SiteDesign design, String stylesheetPath, boolean preview, Long currentPageId) {
        String schoolName = schools.findSingleton().map(School::getName).orElse("Colegio");
        String version = ThemeStylesheet.of(design).version();
        BrandService.Brand brand = brands.current();
        Function<Page, String> link = page -> linkTo(page, preview);
        return new SiteContext(
                schoolName,
                brand.logo(),
                brand.faviconHref(),
                Theme.of(design).id(),
                stylesheetPath + "?v=" + version,
                preview ? PREVIEW_ROOT : "/",
                menus.tree(MenuLocation.HEADER, link, preview, currentPageId),
                menus.tree(MenuLocation.FOOTER, link, preview, currentPageId),
                footer.current(),
                alerts.findVisibleAt(clock.instant()),
                preview);
    }
}
