package cl.colegiosaas.news;

import cl.colegiosaas.site.SiteSection;

import java.util.Set;

/** Lo que el editor escribe en una noticia (todo menos el flujo de aprobación). */
public record NewsDraft(
        String title,
        String slug,
        String summary,
        String body,
        Long categoryId,
        Set<Long> gradeLevelIds,
        SiteSection section,
        String metaTitle,
        String metaDescription,
        Long featuredImageId) {

    public NewsDraft {
        gradeLevelIds = gradeLevelIds == null ? Set.of() : Set.copyOf(gradeLevelIds);
    }
}
