package cl.colegiosaas.news;

import cl.colegiosaas.media.StoredFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Page<Announcement> findByPublishedAtIsNotNullAndCommunityOnlyFalseOrderByPublishedAtDesc(Pageable page);

    @EntityGraph(attributePaths = {"author", "attachment", "gradeLevels", "courses", "courses.gradeLevel"})
    Optional<Announcement> findWithDetailsById(Long id);

    /** ¿El archivo es el adjunto de algún comunicado publicado y público? */
    boolean existsByAttachmentAndPublishedAtIsNotNullAndCommunityOnlyFalse(StoredFile attachment);
}
