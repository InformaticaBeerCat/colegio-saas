package cl.colegiosaas.documents;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstitutionalDocumentRepository extends JpaRepository<InstitutionalDocument, Long> {

    @EntityGraph(attributePaths = "versions")
    Optional<InstitutionalDocument> findBySlug(String slug);

    /** Para la página de documentos y la tarea que revisa vencimientos (DOC-04). */
    @EntityGraph(attributePaths = "versions")
    List<InstitutionalDocument> findAllByOrderByCategoryAscSortOrderAsc();
}
