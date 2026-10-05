package cl.colegiosaas.page;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PageRepositoryTest {

    @Autowired
    PageRepository pages;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    final List<Block> homeBlocks = List.of(
            new Block.Hero("Bienvenidos", "Educamos desde 1985", 10L, null, "Admisión 2027", "/admision"),
            new Block.Stats("En cifras", List.of(new Block.Stat("1.200", "estudiantes"), new Block.Stat("40", "años"))),
            new Block.LatestNews("Noticias", 3),
            new Block.CallToAction("¿Quieres conocernos?", "Agenda una visita guiada", "Agendar", "/visitas"));

    @Test
    void blocksKeepTheirConcreteTypeAfterReload() {
        Page home = new Page("inicio", "Inicio", PageKind.HOME);
        home.editBlocks(homeBlocks);
        pages.saveAndFlush(home);
        em.clear();

        Page loaded = pages.findBySlug("inicio").orElseThrow();
        assertThat(loaded.getDraftBlocks()).isEqualTo(homeBlocks);
        assertThat(loaded.getDraftBlocks().get(0)).isInstanceOf(Block.Hero.class);
        assertThat(loaded.getDraftBlocks().get(1))
                .isInstanceOfSatisfying(Block.Stats.class, stats -> assertThat(stats.items()).hasSize(2));
    }

    @Test
    void eachBlockIsStoredWithItsTypeName() {
        Page home = new Page("inicio", "Inicio", PageKind.HOME);
        home.editBlocks(homeBlocks);
        pages.saveAndFlush(home);

        String json = jdbc.queryForObject("select draft_blocks from page where slug = 'inicio'", String.class);
        assertThat(json).contains("\"type\":\"hero\"").contains("\"type\":\"call-to-action\"");
    }

    @Test
    void visitorsOnlySeeThePublishedVersion() {
        Page about = new Page("nosotros", "Nosotros", PageKind.ABOUT);
        about.editBlocks(List.of(new Block.RichText("<p>Historia</p>")));
        about.publish();
        pages.saveAndFlush(about);
        em.clear();

        Page loaded = pages.findBySlug("nosotros").orElseThrow();
        loaded.editBlocks(List.of(new Block.RichText("<p>Historia corregida</p>")));
        em.flush();
        em.clear();

        Page reloaded = pages.findBySlug("nosotros").orElseThrow();
        assertThat(reloaded.isPublished()).isTrue();
        assertThat(reloaded.hasUnpublishedChanges()).isTrue();
        assertThat(reloaded.getPublishedBlocks()).containsExactly(new Block.RichText("<p>Historia</p>"));
    }

    @Test
    void slugIsUnique() {
        pages.saveAndFlush(new Page("admision", "Admisión", PageKind.CUSTOM));

        assertThatThrownBy(() -> pages.saveAndFlush(new Page("admision", "Otra", PageKind.CUSTOM)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
