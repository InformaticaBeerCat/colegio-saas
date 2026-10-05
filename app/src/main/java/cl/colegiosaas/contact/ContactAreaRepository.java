package cl.colegiosaas.contact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactAreaRepository extends JpaRepository<ContactArea, Long> {

    List<ContactArea> findByActiveTrueOrderBySortOrderAsc();
}
