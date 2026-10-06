package cl.colegiosaas.contact;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByTicketCode(String ticketCode);

    /** Bandeja del panel (COM-02): filtrar por área y estado, lo más antiguo primero. */
    Page<Inquiry> findByAreaAndStatusInOrderByCreatedAtAsc(ContactArea area, Collection<InquiryStatus> statuses, Pageable page);

    List<Inquiry> findByEmailHash(String emailHash);

    List<Inquiry> findByCreatedAtBeforeAndEmailHashNot(Instant cutoff, String excludedHash);
}
