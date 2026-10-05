package cl.colegiosaas.web;

import cl.colegiosaas.consent.ConsentChannel;
import cl.colegiosaas.consent.ConsentMethod;
import cl.colegiosaas.consent.ConsentRegistry;
import cl.colegiosaas.consent.Student;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.Album;
import cl.colegiosaas.media.AlbumService;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaAssetRepository;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.media.ReviewStatus;
import cl.colegiosaas.page.Block;
import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageKind;
import cl.colegiosaas.page.PageService;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.StructureService;
import cl.colegiosaas.support.TestImages;
import cl.colegiosaas.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Year;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fotos y autorización de imagen de punta a punta: subida (MED-01..03, MED-10), revisión del gestor
 * (MED-06), difuminado (MED-07), retiro por revocación (MED-09), nombres junto a fotos (MED-12),
 * texto alternativo (ACC-02) y álbumes (MED-05).
 */
class MediaWorkflowTest extends WebTestSupport {

    @Autowired
    MediaLibrary library;

    @Autowired
    MediaAssetRepository assets;

    @Autowired
    ConsentRegistry registry;

    @Autowired
    AlbumService albums;

    @Autowired
    StructureService structure;

    @Autowired
    PageService pages;

    UserAccount admin;
    UserAccount editor;
    UserAccount manager;
    Course course;

    @BeforeEach
    void setUp() {
        admin = install();
        editor = activeUser("comunicaciones@colegio.cl", Role.EDITOR);
        manager = activeUser("consentimientos@colegio.cl", Role.CONSENT_MANAGER);
        structure.loadChileanLevels();
        long first = structure.levels().stream().filter(l -> l.getName().equals("1° Básico")).findFirst().orElseThrow().getId();
        course = structure.addCourse(first, "A", Year.now().getValue());
        registry.publishBaseForm(admin.getId());
    }

    @Test
    void bulkUploadReportsEachFileAndPhotosStayHiddenUntilReviewed() throws Exception {
        mvc.perform(multipart("/admin/media/upload")
                        .file(new MockMultipartFile("files", "acto.jpg", "image/jpeg", TestImages.jpegWithExif(1200, 800, 1, "GPS secreto")))
                        .file(new MockMultipartFile("files", "notas.txt", "text/plain", "hola".getBytes()))
                        .param("tags", "Aniversario, 2026")
                        .with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("notice", containsString("1 foto(s) subida(s)")))
                .andExpect(flash().attribute("problem", containsString("notas.txt: Formato no admitido")));

        MediaAsset asset = assets.findAll().getFirst();
        assertThat(asset.getReviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(asset.getFile().getVariants()).isNotEmpty();
        String original = "/medios/" + asset.getFile().getSha256() + "/original.jpg";

        // Pendiente: el público no la ve; el panel sí (para revisarla).
        mvc.perform(get(original)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/media/files/" + asset.getFile().getSha256() + "/original.jpg").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/admin/media").param("etiqueta", "aniversario").with(as(editor)))
                .andExpect(content().string(containsString("acto.jpg")));
    }

    @Test
    void photosWithStudentsWithoutConsentNeedBlurBeforeApproval() throws Exception {
        MediaAsset photo = upload("patio.jpg");
        Student withConsent = registry.addStudent("Ana María Rojas Soto", course.getId(), null);
        Student without = registry.addStudent("Diego Ignacio Fuentes Lara", course.getId(), null);
        registry.grant(withConsent.getId(), ConsentChannel.WEBSITE, "Carolina Soto", ConsentMethod.PAPER_FORM, null, manager.getId());

        mvc.perform(post("/admin/review/" + photo.getId() + "/tag").with(as(manager)).with(csrf())
                .param("studentIds", withConsent.getId().toString(), without.getId().toString()));
        mvc.perform(get("/admin/review/" + photo.getId()).with(as(manager)))
                .andExpect(content().string(containsString("Sin autorización")))
                .andExpect(content().string(containsString("Autorizado para el sitio")));

        // Sin texto alternativo no se aprueba (ACC-02); sin difuminar tampoco.
        mvc.perform(post("/admin/review/" + photo.getId() + "/approve").with(as(manager)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("Diego Ignacio Fuentes Lara")));
        mvc.perform(post("/admin/media/" + photo.getId() + "/blur").with(as(manager)).with(csrf()).param("regions", "60,10,25,30"))
                .andExpect(flash().attribute("notice", containsString("Difuminado aplicado")));
        mvc.perform(post("/admin/review/" + photo.getId() + "/approve").with(as(manager)).with(csrf()).param("altText", "Recreo en el patio"))
                .andExpect(flash().attribute("problem", containsString("Confirma que quedaron difuminados")));
        mvc.perform(post("/admin/review/" + photo.getId() + "/approve").with(as(manager)).with(csrf())
                        .param("altText", "Recreo en el patio").param("confirmedBlurred", "true"))
                .andExpect(flash().attribute("notice", containsString("Foto aprobada")));

        MediaAsset approved = library.get(photo.getId());
        assertThat(approved.isDisplayable()).isTrue();
        // El público recibe solo la versión difuminada; la original no se entrega aunque se conozca su huella.
        mvc.perform(get("/medios/" + approved.getBlurredFile().getSha256() + "/original.jpg")).andExpect(status().isOk());
        mvc.perform(get("/medios/" + approved.getFile().getSha256() + "/original.jpg")).andExpect(status().isNotFound());
        String webp = approved.getBlurredFile().getVariants().stream().filter(v -> v.format().equals("webp")).findFirst().orElseThrow()
                .width() + ".webp";
        mvc.perform(get("/medios/" + approved.getBlurredFile().getSha256() + "/" + webp))
                .andExpect(header().string("Content-Type", "image/webp"));
    }

    @Test
    void revokingTheWebsiteConsentWithdrawsEveryPhotoOfTheStudent() throws Exception {
        MediaAsset photo = upload("graduacion.jpg");
        Student student = registry.addStudent("Tomás Andrés Vidal Paz", course.getId(), null);
        registry.grant(student.getId(), ConsentChannel.WEBSITE, "Paula Paz", ConsentMethod.EMAIL, null, manager.getId());
        mvc.perform(post("/admin/review/" + photo.getId() + "/tag").with(as(manager)).with(csrf()).param("studentIds", student.getId().toString()));
        mvc.perform(post("/admin/review/" + photo.getId() + "/approve").with(as(manager)).with(csrf()).param("altText", "Licenciatura"));
        String url = "/medios/" + photo.getFile().getSha256() + "/original.jpg";
        mvc.perform(get(url)).andExpect(status().isOk());

        mvc.perform(post("/admin/students/" + student.getId() + "/consents/revoke").with(as(manager)).with(csrf())
                        .param("channel", "WEBSITE").param("note", "La familia lo pidió por correo"))
                .andExpect(flash().attribute("notice", containsString("1 foto(s) retirada(s)")));

        assertThat(library.get(photo.getId()).isWithdrawn()).isTrue();
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void withdrawingAPhotoAlsoWithdrawsItsDuplicates() throws Exception {
        MediaAsset first = upload("repetida.jpg");
        MediaAsset second = upload("repetida-otra-vez.jpg");
        assertThat(second.getFile()).isEqualTo(first.getFile());
        library.updateAltText(first.getId(), "Acto");
        library.updateAltText(second.getId(), "Acto");
        mvc.perform(post("/admin/review/" + first.getId() + "/exempt").with(as(manager)).with(csrf()));
        mvc.perform(post("/admin/review/" + second.getId() + "/exempt").with(as(manager)).with(csrf()));
        String url = "/medios/" + first.getFile().getSha256() + "/original.jpg";
        mvc.perform(get(url)).andExpect(status().isOk());

        mvc.perform(post("/admin/media/" + first.getId() + "/withdraw").with(as(manager)).with(csrf()).param("reason", "Pedido de la familia"));

        assertThat(library.get(second.getId()).isWithdrawn()).isTrue();
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void captionsCannotCarryAStudentsFullName() throws Exception {
        MediaAsset photo = upload("premiacion.jpg");
        registry.addStudent("Valentina Paz Morales Ruiz", course.getId(), null);

        mvc.perform(post("/admin/media/" + photo.getId()).with(as(editor)).with(csrf())
                        .param("altText", "Premiación").param("caption", "Felicitamos a valentina morales por su logro"))
                .andExpect(flash().attribute("problem", containsString("MED-12")));
        mvc.perform(post("/admin/media/" + photo.getId()).with(as(editor)).with(csrf())
                        .param("altText", "Premiación").param("caption", "Felicitamos a Valentina, de 1° Básico A"))
                .andExpect(flash().attribute("notice", "Datos guardados"));
    }

    @Test
    void albumsPublishOnlyWithEveryPhotoReviewedAndCourseAlbumsStayPrivate() throws Exception {
        MediaAsset photo = upload("fonda.jpg");
        Album album = albums.create("Fonda escolar", null);
        albums.addAssets(album.getId(), List.of(photo.getId()));

        mvc.perform(post("/admin/albums/" + album.getId() + "/publish").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("pendientes de revisión")));

        library.updateAltText(photo.getId(), "Baile de cueca en la fonda");
        mvc.perform(post("/admin/review/" + photo.getId() + "/exempt").with(as(manager)).with(csrf()));
        mvc.perform(post("/admin/albums/" + album.getId() + "/publish").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("notice", "Álbum publicado"));

        mvc.perform(get("/galerias")).andExpect(content().string(containsString("Fonda escolar")));
        mvc.perform(get("/galerias/" + album.getSlug()))
                .andExpect(content().string(containsString("alt=\"Baile de cueca en la fonda\"")))
                .andExpect(content().string(containsString("type=\"image/webp\"")))
                .andExpect(content().string(containsString("loading=\"lazy\"")));

        albums.update(album.getId(), "Fonda escolar", null, null, cl.colegiosaas.media.AlbumVisibility.COURSE, course.getId());
        mvc.perform(get("/galerias/" + album.getSlug())).andExpect(status().isNotFound());
        mvc.perform(get("/galerias")).andExpect(content().string(not(containsString("Fonda escolar"))));
    }

    @Test
    void heroGalleryAndNewsOnlyUseApprovedPhotos() throws Exception {
        MediaAsset photo = upload("fachada.jpg");
        Page home = pages.create("Inicio", "inicio", PageKind.HOME);
        pages.addBlock(home.getId(), new Block.Hero("Bienvenidos", null, photo.getId(), null, null, null));
        pages.publish(home.getId());

        // Pendiente: la portada se muestra sin la foto.
        mvc.perform(get("/")).andExpect(content().string(not(containsString("hero__image"))));
        library.updateAltText(photo.getId(), "Fachada del colegio");
        mvc.perform(post("/admin/review/" + photo.getId() + "/exempt").with(as(manager)).with(csrf()));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("hero--with-image")))
                .andExpect(content().string(containsString("alt=\"Fachada del colegio\"")))
                .andExpect(content().string(containsString("fetchpriority=\"high\"")));

        MediaAsset pending = upload("pendiente.jpg");
        mvc.perform(post("/admin/news").with(as(admin)).with(csrf()).param("title", "Nueva fachada"));
        long newsId = mvc.perform(get("/admin/news").with(as(admin))).andReturn().getModelAndView().getModel()
                .get("articles") instanceof List<?> list ? ((cl.colegiosaas.news.NewsArticle) list.getFirst()).getId() : 0;
        mvc.perform(post("/admin/news/" + newsId).with(as(admin)).with(csrf())
                        .param("title", "Nueva fachada").param("featuredImageId", pending.getId().toString()))
                .andExpect(content().string(containsString("foto aprobada")));
    }

    @Test
    void usedPhotosCannotBeDeletedAndRolesAreSeparated() throws Exception {
        MediaAsset photo = upload("usada.jpg");
        Album album = albums.create("Usada", null);
        albums.addAssets(album.getId(), List.of(photo.getId()));

        mvc.perform(post("/admin/media/" + photo.getId() + "/delete").with(as(editor)).with(csrf()))
                .andExpect(flash().attribute("problem", containsString("retíralo en vez de eliminarlo")));

        mvc.perform(get("/admin/review").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/students").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/media/upload").file(new MockMultipartFile("files", "x.jpg", "image/jpeg", TestImages.jpeg(10, 10)))
                .with(as(manager)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void logoUploadedByTheDesignerIsShownWithoutReview() throws Exception {
        mvc.perform(multipart("/admin/design/logo").file(new MockMultipartFile("file", "logo.png", "image/png", TestImages.transparentPng(300, 300)))
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("notice", "Logo actualizado"));

        mvc.perform(get("/")).andExpect(content().string(containsString("class=\"site-brand__logo\"")));
    }

    private MediaAsset upload(String name) {
        List<MediaLibrary.UploadResult> results = library.uploadImages(
                List.of(new MediaLibrary.Upload(name, TestImages.jpeg(1000, 700))), editor.getId(), null, Set.of());
        assertThat(results.getFirst().ok()).as(results.getFirst().problem()).isTrue();
        return library.get(results.getFirst().assetId());
    }
}
