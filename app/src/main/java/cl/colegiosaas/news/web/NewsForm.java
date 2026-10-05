package cl.colegiosaas.news.web;

import cl.colegiosaas.news.NewsArticle;
import cl.colegiosaas.news.NewsDraft;
import cl.colegiosaas.site.SiteSection;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class NewsForm {

    @Size(max = 200)
    private String title;
    @Size(max = 120)
    private String slug;
    @Size(max = 500, message = "La bajada admite hasta 500 caracteres")
    private String summary;
    private String body;
    private Long categoryId;
    private Set<Long> gradeLevelIds = new HashSet<>();
    private SiteSection section;
    @Size(max = 120)
    private String metaTitle;
    @Size(max = 300)
    private String metaDescription;
    private Long featuredImageId;

    static NewsForm of(NewsArticle article) {
        NewsForm form = new NewsForm();
        form.title = article.getTitle();
        form.slug = article.getSlug();
        form.summary = article.getSummary();
        form.body = article.getBody();
        form.categoryId = article.getCategory() == null ? null : article.getCategory().getId();
        form.gradeLevelIds = article.getGradeLevels().stream().map(GradeLevel::getId).collect(Collectors.toSet());
        form.section = article.getSection();
        form.metaTitle = article.getSeo() == null ? null : article.getSeo().metaTitle();
        form.metaDescription = article.getSeo() == null ? null : article.getSeo().metaDescription();
        form.featuredImageId = article.getFeaturedImage() == null ? null : article.getFeaturedImage().getId();
        return form;
    }

    NewsDraft toDraft() {
        return new NewsDraft(title, slug, summary, body, categoryId, gradeLevelIds, section, metaTitle, metaDescription,
                featuredImageId);
    }
}
