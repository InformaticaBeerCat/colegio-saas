package cl.colegiosaas.media;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /** Cola de trabajo del gestor de consentimientos: lo más antiguo primero. */
    @EntityGraph(attributePaths = {"file", "blurredFile", "uploadedBy"})
    List<MediaAsset> findByReviewStatusOrderByCreatedAtAsc(ReviewStatus reviewStatus);

    List<MediaAsset> findByTags_NameOrderByCreatedAtDesc(String tagName);

    List<MediaAsset> findByFolderOrderByCreatedAtDesc(MediaFolder folder);

    /** La biblioteca completa, lo más reciente primero, con lo que muestra la grilla. */
    @Query("""
            select distinct a from MediaAsset a
            left join fetch a.file left join fetch a.blurredFile left join fetch a.folder left join fetch a.tags
            order by a.createdAt desc
            """)
    List<MediaAsset> findAllForLibrary();

    @EntityGraph(attributePaths = {"file", "blurredFile", "folder", "tags", "uploadedBy", "reviewedBy"})
    Optional<MediaAsset> findWithDetailsById(Long id);

    /** Copias de la misma foto (subida más de una vez): comparten archivo. */
    List<MediaAsset> findByFile(StoredFile file);

    /** Medios que usan el archivo, como original o como versión difuminada. */
    @Query("""
            select a from MediaAsset a
            where a.blurredFile = :file or a.file = :file
            """)
    List<MediaAsset> findByPublicFile(StoredFile file);

    /** Usos que impiden borrar: álbumes, diseño del sitio, noticias, talleres y etiquetas de estudiantes. */
    @Query("""
            select (select count(i) from AlbumItem i where i.asset = :asset)
                 + (select count(al) from Album al where al.cover = :asset)
                 + (select count(s) from SiteSettings s where s.logo = :asset or s.favicon = :asset)
                 + (select count(n) from NewsArticle n where n.featuredImage = :asset or n.video = :asset)
                 + (select count(w) from Workshop w where w.image = :asset)
                 + (select count(sa) from StudentAppearance sa where sa.asset = :asset)
            from MediaAsset m where m = :asset
            """)
    long usageCount(MediaAsset asset);
}
