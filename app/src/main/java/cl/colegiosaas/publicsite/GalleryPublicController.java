package cl.colegiosaas.publicsite;

import cl.colegiosaas.media.Album;
import cl.colegiosaas.media.AlbumService;
import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.RequiresFeature;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Galerías públicas (MED-05): solo álbumes públicos y publicados, y en ellos solo fotos aprobadas. */
@Controller
@RequiresFeature(Feature.GALLERIES)
class GalleryPublicController {

    private final AlbumService albums;
    private final PublicPages pages;

    GalleryPublicController(AlbumService albums, PublicPages pages) {
        this.albums = albums;
        this.pages = pages;
    }

    @GetMapping("/galerias")
    String list(Model model) {
        pages.prepare(model, "Galerías", "Fotos de las actividades del colegio");
        model.addAttribute("albums", albums.publicAlbums());
        return "public/gallery/list";
    }

    @GetMapping("/galerias/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}")
    String album(@PathVariable String slug, Model model) {
        Album album = albums.publicAlbum(slug);
        pages.prepare(model, album.getTitle(), album.getDescription());
        model.addAttribute("album", album);
        model.addAttribute("assets", album.visibleAssets());
        return "public/gallery/album";
    }
}
