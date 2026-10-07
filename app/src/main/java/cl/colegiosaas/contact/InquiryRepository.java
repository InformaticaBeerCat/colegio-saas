package cl.colegiosaas.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByTicketCode(String ticketCode);

    /** Bandeja del panel (COM-02): filtrar por área y estado, lo más antiguo primero. */
    Page<Inquiry> findByAreaAndStatusInOrderByCreatedAtAsc(ContactArea area, Collection<InquiryStatus> statuses, Pageable page);

    List<Inquiry> findByEmailHash(String emailHash);

    List<Inquiry> findByCreatedAtBeforeAndEmailHashNot(Instant cutoff, String excludedHash);

    /** Bandeja: lo más antiguo arriba, para responder en orden de llegada. */
    @EntityGraph(attributePaths = {"area", "assignedTo"})
    List<Inquiry> findTop200ByStatusInOrderByCreatedAtAsc(Collection<InquiryStatus> statuses);

    @EntityGraph(attributePaths = {"area", "assignedTo"})
    List<Inquiry> findTop200ByAreaAndStatusInOrderByCreatedAtAsc(ContactArea area, Collection<InquiryStatus> statuses);

    @EntityGraph(attributePaths = {"area", "assignedTo", "consent"})
    Optional<Inquiry> findWithAreaById(Long id);

    long countByStatus(InquiryStatus status);

    /** Respondidas desde una fecha: para el tiempo de primera respuesta (COM-02). */
    List<Inquiry> findByFirstResponseAtAfter(Instant since);
}
