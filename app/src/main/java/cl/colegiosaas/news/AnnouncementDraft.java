package cl.colegiosaas.news;

import java.util.Set;

/** Lo editable de un comunicado. Según {@code audience} se usan los niveles o los cursos. */
public record AnnouncementDraft(String title, String body, AnnouncementAudience audience,
                                Set<Long> gradeLevelIds, Set<Long> courseIds) {

    public AnnouncementDraft {
        gradeLevelIds = gradeLevelIds == null ? Set.of() : Set.copyOf(gradeLevelIds);
        courseIds = courseIds == null ? Set.of() : Set.copyOf(courseIds);
    }
}
