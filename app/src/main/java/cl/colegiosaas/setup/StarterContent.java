package cl.colegiosaas.setup;

import cl.colegiosaas.documents.DocumentCategory;
import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.MenuItem;
import cl.colegiosaas.page.MenuLocation;
import cl.colegiosaas.page.MenuService;
import cl.colegiosaas.page.MenuService.MenuTarget;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageRepository;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final SchoolRepository schools;

    StarterContent(PageService pages, PageRepository pageRepository, MenuService menus, SchoolRepository schools) {
        this.pages = pages;
        this.pageRepository = pageRepository;
        this.menus = menus;
        this.schools = schools;
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
                "Presenta al equipo de convivencia escolar y su forma de trabajo.");
        if (coexistence.getDraftBlocks().size() == 1) {
            pages.addBlock(coexistence.getId(), new Block.Documents("Protocolos y plan de convivencia",
                    List.of(DocumentCategory.PROTOCOL, DocumentCategory.COEXISTENCE_PLAN)));
            pages.addBlock(coexistence.getId(), new Block.ReportChannel("Canal de denuncia",
                    "Si sufres o presencias una situación de maltrato, acoso o discriminación, avísanos. "
                            + "Toda denuncia se recibe con reserva y se responde según el protocolo correspondiente.",
                    null, null, null, null));
        }

        menus.add(MenuLocation.HEADER, new MenuTarget("Inicio", home.getId(), null, null));
        MenuItem aboutItem = menus.add(MenuLocation.HEADER, new MenuTarget("Quiénes somos", about.getId(), null, null));
        menus.add(MenuLocation.HEADER, new MenuTarget("Proyecto Educativo", pei.getId(), null, aboutItem.getId()));
        menus.add(MenuLocation.HEADER, new MenuTarget("Infraestructura", facilities.getId(), null, aboutItem.getId()));
        menus.add(MenuLocation.HEADER, new MenuTarget("Niveles", levels.getId(), null, null));
        menus.add(MenuLocation.HEADER, new MenuTarget("Convivencia escolar", coexistence.getId(), null, null));

        // Secciones de los módulos: solo las que el plan del colegio incluye (las demás responderían 404).
        School school = schools.findSingleton().orElse(null);
        if (school != null && school.hasFeature(Feature.NEWS)) {
            menus.add(MenuLocation.HEADER, new MenuTarget("Noticias", null, "/noticias", null));
        }
        if (school != null && school.hasFeature(Feature.CALENDAR)) {
            menus.add(MenuLocation.HEADER, new MenuTarget("Calendario", null, "/calendario", null));
        }
        menus.add(MenuLocation.FOOTER, new MenuTarget("Documentos institucionales", null, "/documentos", null));
        menus.add(MenuLocation.FOOTER, new MenuTarget("Comunicados", null, "/comunicados", null));
        menus.add(MenuLocation.FOOTER, new MenuTarget("Preguntas frecuentes", null, "/preguntas-frecuentes", null));
        menus.add(MenuLocation.FOOTER, new MenuTarget("Útiles, uniforme y minuta", null, "/informacion-practica", null));
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
