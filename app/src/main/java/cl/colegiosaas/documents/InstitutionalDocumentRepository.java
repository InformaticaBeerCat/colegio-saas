package cl.colegiosaas.documents;

import cl.colegiosaas.media.StoredFile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InstitutionalDocumentRepository extends JpaRepository<InstitutionalDocument, Long> {

    @EntityGraph(attributePaths = {"versions", "versions.file", "parent"})
    Optional<InstitutionalDocument> findBySlug(String slug);

    @EntityGraph(attributePaths = {"versions", "versions.file", "versions.publishedBy", "parent"})
    Optional<InstitutionalDocument> findWithVersionsById(Long id);

    /** Para la página de documentos y la tarea que revisa vencimientos (DOC-04). */
    @EntityGraph(attributePaths = {"versions", "versions.file", "parent"})
    List<InstitutionalDocument> findAllByOrderByCategoryAscSortOrderAsc();

    boolean existsByParent(InstitutionalDocument parent);

    /** Toda versión (vigente o archivada) es pública: el historial es parte de la transparencia (DOC-03). */
    @Query("select count(v) > 0 from DocumentVersion v where v.file = :file")
    boolean isPublishedFile(StoredFile file);
}
