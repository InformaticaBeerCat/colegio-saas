package cl.colegiosaas.media;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    /** Álbum con sus ítems, medios y archivos en una sola consulta (evita N+1 al mostrar la galería). */
    @EntityGraph(attributePaths = {"items", "items.asset", "items.asset.file", "items.asset.blurredFile", "cover", "course"})
    Optional<Album> findBySlug(String slug);

    @EntityGraph(attributePaths = {"items", "items.asset", "items.asset.file", "items.asset.blurredFile", "cover", "course"})
    Optional<Album> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"items", "items.asset", "items.asset.file", "items.asset.blurredFile", "cover"})
    List<Album> findAllByOrderByCreatedAtDesc();

    boolean existsBySlug(String slug);
}
