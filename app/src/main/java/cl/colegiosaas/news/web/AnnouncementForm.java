package cl.colegiosaas.news.web;

import cl.colegiosaas.news.Announcement;
import cl.colegiosaas.news.AnnouncementAudience;
import cl.colegiosaas.news.AnnouncementDraft;
import cl.colegiosaas.news.AnnouncementService;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class AnnouncementForm {

    @Size(max = 200)
    private String title;
    private String body;
    private AnnouncementAudience audience = AnnouncementAudience.EVERYONE;
    private Set<Long> gradeLevelIds = new HashSet<>();
    private Set<Long> courseIds = new HashSet<>();

    static AnnouncementForm of(Announcement a) {
        AnnouncementForm form = new AnnouncementForm();
        form.title = a.getTitle();
        form.body = a.getBody();
        form.audience = a.getAudience();
        form.gradeLevelIds = new HashSet<>(AnnouncementService.ids(a.getGradeLevels()));
        form.courseIds = new HashSet<>(AnnouncementService.ids(a.getCourses()));
        return form;
    }

    AnnouncementDraft toDraft() {
        return new AnnouncementDraft(title, body, audience, gradeLevelIds, courseIds);
    }
}
