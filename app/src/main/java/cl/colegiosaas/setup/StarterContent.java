package cl.colegiosaas.setup;

import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.MenuItem;
import cl.colegiosaas.page.MenuLocation;
import cl.colegiosaas.page.MenuService;
import cl.colegiosaas.page.MenuService.MenuTarget;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageRepository;
import cl.colegiosaas.page.PageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contenido inicial del asistente (CFG-01): la portada publicada y las páginas institucionales en
 * borrador, ya enlazadas en el menú. Las páginas en borrador no aparecen en el menú público hasta que
 * el colegio escriba su contenido y las publique: nunca se publica texto de ejemplo.
 */
@Service
public class StarterContent {

    private final PageService pages;
    private final PageRepository pageRepository;
    private final MenuService menus;

    StarterContent(PageService pages, PageRepository pageRepository, MenuService menus) {
        this.pages = pages;
        this.pageRepository = pageRepository;
        this.menus = menus;
    }

    /** Ya hay portada: el asistente no vuelve a crear páginas. */
    @Transactional(readOnly = true)
    public boolean exists() {
        return pageRepository.existsByKind(PageKind.HOME);
    }

    @Transactional
    public void create(String schoolName) {
        if (exists()) {
            return;
        }
        Page home = pages.create("Inicio", "inicio", PageKind.HOME);
        pages.addBlock(home.getId(), new Block.Hero(schoolName, "Bienvenidos a nuestra comunidad educativa",
                null, null, "Conócenos", "/quienes-somos"));
        pages.addBlock(home.getId(), new Block.LatestNews("Noticias", 3));
        pages.addBlock(home.getId(), new Block.UpcomingEvents("Próximos eventos", 3));
        pages.addBlock(home.getId(), new Block.Location("Dónde estamos"));
        pages.publish(home.getId());

        Page about = draft("Quiénes somos", "quienes-somos", PageKind.ABOUT,
                "Cuenta la historia del colegio, su misión y su visión.");
        Page pei = draft("Proyecto Educativo", "proyecto-educativo", PageKind.PEI,
                "Resume el Proyecto Educativo Institucional y enlaza el documento completo.");
        Page facilities = draft("Infraestructura", "infraestructura", PageKind.FACILITIES,
                "Describe las salas, laboratorios, biblioteca y espacios deportivos.");
        Page levels = draft("Niveles", "niveles", PageKind.LEVELS,
                "Presenta los niveles que imparte el colegio, desde párvulos hasta media.");
        Page coexistence = draft("Convivencia escolar", "convivencia-escolar", PageKind.COEXISTENCE,
                "Presenta al equipo de convivencia, los protocolos y el canal de denuncia.");

        menus.add(MenuLocation.HEADER, new MenuTarget("Inicio", home.getId(), null, null));
        MenuItem aboutItem = menus.add(MenuLocation.HEADER, new MenuTarget("Quiénes somos", about.getId(), null, null));
        menus.add(MenuLocation.HEADER, new MenuTarget("Proyecto Educativo", pei.getId(), null, aboutItem.getId()));
        menus.add(MenuLocation.HEADER, new MenuTarget("Infraestructura", facilities.getId(), null, aboutItem.getId()));
        menus.add(MenuLocation.HEADER, new MenuTarget("Niveles", levels.getId(), null, null));
        menus.add(MenuLocation.HEADER, new MenuTarget("Convivencia escolar", coexistence.getId(), null, null));
    }

    /**
     * Página en borrador con una indicación de qué escribir; solo la ven los editores.
     * Si el colegio ya creó una de ese tipo, se usa esa.
     */
    private Page draft(String title, String slug, PageKind kind, String hint) {
        Page existing = pageRepository.findFirstByKind(kind).orElse(null);
        if (existing != null) {
            return existing;
        }
        Page page = pages.create(title, pageRepository.existsBySlug(slug) ? null : slug, kind);
        pages.addBlock(page.getId(), new Block.RichText("<p><em>Borrador: " + hint + "</em></p>"));
        return page;
    }
}
