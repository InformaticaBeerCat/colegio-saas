package cl.colegiosaas.info;

import cl.colegiosaas.support.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class InfoRepositoryTest {

    @Autowired
    FaqCategoryRepository categories;

    @Autowired
    FaqEntryRepository entries;

    @Autowired
    TestEntityManager em;

    @Test
    void faqIsListedByCategoryThenEntryOrder() {
        FaqCategory uniform = categories.save(new FaqCategory("Uniforme", 2));
        FaqCategory admissions = categories.save(new FaqCategory("Admisión", 1));
        entries.save(new FaqEntry(uniform, "¿Dónde se compra?", "En la tienda del CEPA.", 1));
        entries.save(new FaqEntry(admissions, "¿Cuándo postulo?", "En agosto, por el SAE.", 2));
        entries.save(new FaqEntry(admissions, "¿Hay visitas?", "Sí, agéndalas en línea.", 1));
        FaqEntry hidden = new FaqEntry(admissions, "Borrador", "…", 3);
        hidden.setPublished(false);
        entries.save(hidden);
        em.flush();
        em.clear();

        assertThat(entries.findByPublishedTrueOrderByCategory_SortOrderAscSortOrderAsc())
                .extracting(FaqEntry::getQuestion)
                .containsExactly("¿Hay visitas?", "¿Cuándo postulo?", "¿Dónde se compra?");
    }

    @Test
    void monthlyMenuIsCurrentOnlyWithinItsDates() {
        InfoSheet october = new InfoSheet(InfoSheetKind.MENU, "Minuta octubre", 2026);
        october.setValidFrom(LocalDate.of(2026, 10, 1));
        october.setValidUntil(LocalDate.of(2026, 10, 31));
        october.setPublished(true);

        assertThat(october.isCurrentOn(LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(october.isCurrentOn(LocalDate.of(2026, 11, 1))).isFalse();
    }
}
