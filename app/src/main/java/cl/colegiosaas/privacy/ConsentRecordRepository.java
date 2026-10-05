package cl.colegiosaas.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    /** Todos los consentimientos de una persona, buscados por el índice ciego de su email. */
    List<ConsentRecord> findBySubjectEmailHashOrderByCreatedAtDesc(String subjectEmailHash);
}
