package cl.colegiosaas.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaFolderRepository extends JpaRepository<MediaFolder, Long> {

    List<MediaFolder> findByParentIsNullOrderByNameAsc();

    List<MediaFolder> findByParentOrderByNameAsc(MediaFolder parent);
}
