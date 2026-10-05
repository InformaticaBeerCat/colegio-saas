package cl.colegiosaas.news;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.structure.EducationStage;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.support.RepositoryTest;
import cl.colegiosaas.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class NewsRepositoryTest {

    @Autowired
    NewsArticleRepository news;

    @Autowired
    AnnouncementRepository announcements;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    TestEntityManager em;

    final Instant now = Instant.parse("2026-10-05T15:00:00Z");

    @Test
    void visibleNewsIncludesScheduledOnesThatAreDue() {
        UserAccount editor = fixtures.user("editor@colegio.cl", Role.EDITOR);
        news.save(approved("publicada", editor, null));
        news.save(approved("programada-vencida", editor, now.minus(Duration.ofMinutes(5))));
        news.save(approved("programada-futura", editor, now.plus(Duration.ofDays(2))));
        news.save(new NewsArticle("borrador", "Borrador", editor));
        em.flush();

        assertThat(news.findVisibleAt(now, PageRequest.of(0, 10)))
                .extracting(NewsArticle::getSlug)
                .containsExactlyInAnyOrder("publicada", "programada-vencida");
    }

    @Test
    void longBodiesAndGradeLevelTagsArePersisted() {
        UserAccount editor = fixtures.user("editor@colegio.cl", Role.EDITOR);
        GradeLevel first = fixtures.level("1° Básico", EducationStage.PRIMARY, 1);
        NewsArticle article = new NewsArticle("largo", "Crónica larga", editor);
        article.setBody("<p>" + "Texto de la crónica. ".repeat(10_000) + "</p>");
        article.tagGradeLevel(first);
        news.saveAndFlush(article);
        em.clear();

        NewsArticle loaded = news.findBySlug("largo").orElseThrow();
        assertThat(loaded.getBody()).hasSizeGreaterThan(200_000);
        assertThat(loaded.getGradeLevels()).extracting(GradeLevel::getName).containsExactly("1° Básico");
    }

    @Test
    void announcementsCanTargetSpecificGradeLevels() {
        UserAccount editor = fixtures.user("editor@colegio.cl", Role.EDITOR);
        GradeLevel kinder = fixtures.level("Kínder", EducationStage.EARLY_CHILDHOOD, 1);
        Announcement circular = new Announcement("Salida pedagógica", editor);
        circular.addressToGradeLevels(Set.of(kinder));
        circular.publish();
        announcements.saveAndFlush(circular);
        em.clear();

        Announcement loaded = announcements.findById(circular.getId()).orElseThrow();
        assertThat(loaded.getAudience()).isEqualTo(AnnouncementAudience.GRADE_LEVELS);
        assertThat(loaded.getGradeLevels()).hasSize(1);
        assertThat(announcements.findByPublishedAtIsNotNullAndCommunityOnlyFalseOrderByPublishedAtDesc(PageRequest.of(0, 5)))
                .hasSize(1);
    }

    private NewsArticle approved(String slug, UserAccount editor, Instant publishAt) {
        NewsArticle article = new NewsArticle(slug, slug, editor);
        article.submitForReview();
        article.approve(editor, publishAt, publishAt == null ? now : publishAt.minus(Duration.ofDays(1)));
        return article;
    }
}
