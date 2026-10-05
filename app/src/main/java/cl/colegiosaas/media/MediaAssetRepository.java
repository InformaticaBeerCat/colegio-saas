package cl.colegiosaas.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /** Cola de trabajo del gestor de consentimientos: lo más antiguo primero. */
    List<MediaAsset> findByReviewStatusOrderByCreatedAtAsc(ReviewStatus reviewStatus);

    List<MediaAsset> findByTags_NameOrderByCreatedAtDesc(String tagName);

    List<MediaAsset> findByFolderOrderByCreatedAtDesc(MediaFolder folder);
}
