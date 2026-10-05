package cl.colegiosaas.publicsite;

import cl.colegiosaas.site.SiteDesign;
import cl.colegiosaas.site.SiteDesignService;
import cl.colegiosaas.site.ThemeStylesheet;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Hoja de tokens del tema (CFG-03). La URL lleva la huella del contenido: si coincide, el navegador la
 * guarda un año; al publicar otro diseño cambia la huella y por lo tanto la URL.
 */
@RestController
class ThemeStylesheetController {

    private static final MediaType CSS = MediaType.valueOf("text/css;charset=UTF-8");

    private final SiteDesignService designs;

    ThemeStylesheetController(SiteDesignService designs) {
        this.designs = designs;
    }

    @GetMapping("/site/theme.css")
    ResponseEntity<String> published(@RequestParam(name = "v", required = false) String version) {
        ThemeStylesheet stylesheet = ThemeStylesheet.of(designs.published());
        CacheControl cache = stylesheet.version().equals(version)
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
        return ResponseEntity.ok().contentType(CSS).cacheControl(cache).body(stylesheet.css());
    }

    @GetMapping("/admin/preview-theme.css")
    @PreAuthorize("hasAnyAuthority('PAGES', 'SITE_DESIGN')")
    ResponseEntity<String> draft() {
        SiteDesign draft = designs.draft();
        return ResponseEntity.ok().contentType(CSS).cacheControl(CacheControl.noStore()).body(ThemeStylesheet.of(draft).css());
    }
}
