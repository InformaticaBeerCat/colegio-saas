package cl.colegiosaas.media;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    /** Álbum con sus ítems y medios en una sola consulta (evita N+1 al mostrar la galería). */
    @EntityGraph(attributePaths = {"items", "items.asset"})
    Optional<Album> findBySlug(String slug);
}
