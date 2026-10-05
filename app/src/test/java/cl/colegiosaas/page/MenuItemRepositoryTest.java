package cl.colegiosaas.page;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MenuItemRepositoryTest {

    @Autowired
    MenuItemRepository menu;

    @Autowired
    PageRepository pages;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void menuIsReturnedInOrderWithSubmenus() {
        Page about = pages.save(new Page("nosotros", "Nosotros", PageKind.ABOUT));
        MenuItem colegio = menu.save(MenuItem.toPage(MenuLocation.HEADER, "El colegio", about, 1));
        MenuItem pei = MenuItem.toUrl(MenuLocation.HEADER, "PEI (PDF)", "/documentos/pei", 1);
        pei.setParent(colegio);
        menu.save(pei);
        menu.save(MenuItem.toUrl(MenuLocation.HEADER, "Napsis", "https://www.napsis.cl", 0));
        menu.save(MenuItem.toUrl(MenuLocation.FOOTER, "Privacidad", "/privacidad", 0));
        em.flush();
        em.clear();

        assertThat(menu.findByMenuOrderBySortOrderAscIdAsc(MenuLocation.HEADER))
                .extracting(MenuItem::getLabel)
                .containsExactly("Napsis", "El colegio", "PEI (PDF)");
    }

    @Test
    void databaseRejectsItemsWithoutTargetOrWithBoth() {
        Page about = pages.saveAndFlush(new Page("nosotros", "Nosotros", PageKind.ABOUT));

        assertThatThrownBy(() -> insertRaw("null", "null")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRaw(about.getId().toString(), "'/x'")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pageInTheMenuCannotBeDeleted() {
        Page about = pages.save(new Page("nosotros", "Nosotros", PageKind.ABOUT));
        menu.saveAndFlush(MenuItem.toPage(MenuLocation.HEADER, "Nosotros", about, 0));
        assertThat(menu.existsByPage(about)).isTrue();

        // Sin el menú cargado en memoria, quien protege es la llave foránea de la base.
        em.clear();
        Page loaded = pages.findBySlug("nosotros").orElseThrow();
        assertThatThrownBy(() -> {
            pages.delete(loaded);
            pages.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRaw(String pageId, String url) {
        jdbc.update("""
                insert into menu_item (version, created_at, updated_at, menu, label, page_id, url, sort_order)
                values (0, current_timestamp, current_timestamp, 'HEADER', 'X', %s, %s, 0)
                """.formatted(pageId, url));
    }
}
