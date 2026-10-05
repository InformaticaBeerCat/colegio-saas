package cl.colegiosaas.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuickLinkRepository extends JpaRepository<QuickLink, Long> {

    List<QuickLink> findByActiveTrueOrderBySortOrderAsc();
}
