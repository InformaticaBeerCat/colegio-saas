package cl.colegiosaas.news;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsArticleTest {

    final UserAccount editor = new UserAccount("editor@colegio.cl", "Editora", Role.EDITOR);
    final UserAccount admin = new UserAccount("admin@colegio.cl", "Directora", Role.SCHOOL_ADMIN);
    final Instant now = Instant.parse("2026-10-05T15:00:00Z");

    @Test
    void approvalWithoutDatePublishesImmediately() {
        NewsArticle article = inReview();

        article.approve(admin, null, now);

        assertThat(article.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(article.getPublishedAt()).isEqualTo(now);
        assertThat(article.isVisibleAt(now)).isTrue();
    }

    @Test
    void approvalWithFutureDateSchedulesTheArticle() {
        NewsArticle article = inReview();
        Instant tomorrow = now.plus(Duration.ofDays(1));

        article.approve(admin, tomorrow, now);

        assertThat(article.getStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(article.isVisibleAt(now)).isFalse();
        // Visible al llegar la hora aunque la tarea programada todavía no corra.
        assertThat(article.isVisibleAt(tomorrow)).isTrue();

        article.publishIfDue(tomorrow.plusSeconds(30));
        assertThat(article.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(article.getPublishedAt()).isEqualTo(tomorrow);
    }

    @Test
    void reviewerCanSendItBackWithANote() {
        NewsArticle article = inReview();

        article.returnToDraft(admin, "Falta el texto alternativo de la foto");

        assertThat(article.getStatus()).isEqualTo(NewsStatus.DRAFT);
        assertThat(article.getReviewNote()).contains("texto alternativo");
    }

    @Test
    void draftCannotBeApprovedWithoutGoingThroughReview() {
        NewsArticle article = new NewsArticle("noticia", "Noticia", editor);

        assertThatThrownBy(() -> article.approve(admin, null, now)).isInstanceOf(IllegalStateException.class);
        assertThat(article.isVisibleAt(now)).isFalse();
    }

    private NewsArticle inReview() {
        NewsArticle article = new NewsArticle("feria-ciencias", "Feria de Ciencias", editor);
        article.submitForReview();
        return article;
    }
}
