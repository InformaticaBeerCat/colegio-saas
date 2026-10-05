package cl.colegiosaas.news;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.media.PublicFileLinks;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.shared.html.HtmlSanitizer;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.CourseRepository;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Comunicados y circulares con fecha y destinatarios (NOT-03). */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcements;
    private final GradeLevelRepository gradeLevels;
    private final CourseRepository courses;
    private final UserAccountRepository users;
    private final AuditTrail audit;

    AnnouncementService(AnnouncementRepository announcements, GradeLevelRepository gradeLevels, CourseRepository courses,
                        UserAccountRepository users, AuditTrail audit) {
        this.announcements = announcements;
        this.gradeLevels = gradeLevels;
        this.courses = courses;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Announcement> list() {
        return announcements.findAll().stream()
                .sorted(Comparator.comparing(Announcement::getUpdatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Announcement get(long id) {
        return announcements.findWithDetailsById(id).orElseThrow(() -> new NotFound("El comunicado no existe"));
    }

    @Transactional
    public Announcement create(String title, long authorId) {
        if (title == null || title.isBlank()) {
            throw new RuleViolation("El comunicado necesita un título");
        }
        UserAccount author = users.findById(authorId).orElseThrow();
        Announcement announcement = announcements.save(new Announcement(title.strip(), author));
        audit.record(AuditAction.CREATE, "Announcement", announcement.getId(), announcement.getTitle());
        return announcement;
    }

    @Transactional
    public void update(long id, AnnouncementDraft draft) {
        Announcement announcement = get(id);
        if (draft.title() == null || draft.title().isBlank()) {
            throw new RuleViolation("El comunicado necesita un título");
        }
        announcement.setTitle(draft.title().strip());
        announcement.setBody(HtmlSanitizer.richText(draft.body()));
        AnnouncementAudience audience = draft.audience() == null ? AnnouncementAudience.EVERYONE : draft.audience();
        try {
            switch (audience) {
                case EVERYONE -> announcement.addressToEveryone();
                case GRADE_LEVELS -> announcement.addressToGradeLevels(new HashSet<>(gradeLevels.findAllById(draft.gradeLevelIds())));
                case COURSES -> announcement.addressToCourses(new HashSet<>(courses.findAllById(draft.courseIds())));
            }
        } catch (IllegalArgumentException e) {
            throw new RuleViolation(audience == AnnouncementAudience.COURSES
                    ? "Elige al menos un curso" : "Elige al menos un nivel");
        }
        audit.record(AuditAction.UPDATE, "Announcement", id, announcement.getTitle());
    }

    @Transactional
    public void attach(long id, StoredFile file) {
        Announcement announcement = get(id);
        announcement.setAttachment(file);
        audit.record(AuditAction.UPDATE, "Announcement", id, "Adjunto: " + file.getOriginalName());
    }

    @Transactional
    public void removeAttachment(long id) {
        get(id).setAttachment(null);
    }

    @Transactional
    public void publish(long id) {
        Announcement announcement = get(id);
        if (announcement.getBody().isBlank() && announcement.getAttachment() == null) {
            throw new RuleViolation("Escribe el texto o adjunta la circular antes de publicar");
        }
        announcement.publish();
        audit.record(AuditAction.PUBLISH, "Announcement", id, announcement.getTitle());
    }

    @Transactional
    public void unpublish(long id) {
        Announcement announcement = get(id);
        announcement.unpublish();
        audit.record(AuditAction.UNPUBLISH, "Announcement", id, announcement.getTitle());
    }

    @Transactional
    public void delete(long id) {
        Announcement announcement = get(id);
        if (announcement.isPublished()) {
            throw new RuleViolation("Despublica el comunicado antes de eliminarlo");
        }
        announcements.delete(announcement);
        audit.record(AuditAction.DELETE, "Announcement", id, announcement.getTitle());
    }

    /** Comunicados públicos (los de la zona comunidad llegan en v2), los más recientes primero. */
    @Transactional(readOnly = true)
    public Page<AnnouncementView> published(Pageable page) {
        return announcements.findByPublishedAtIsNotNullAndCommunityOnlyFalseOrderByPublishedAtDesc(page).map(AnnouncementService::view);
    }

    private static AnnouncementView view(Announcement a) {
        StoredFile file = a.getAttachment();
        return new AnnouncementView(a.getTitle(), a.getBody(), a.getPublishedAt(), audience(a),
                file == null ? null : PublicFileLinks.href(file),
                file == null ? null : file.getOriginalName(),
                file == null ? 0 : file.getSizeBytes());
    }

    static String audience(Announcement a) {
        return switch (a.getAudience()) {
            case EVERYONE -> "Todo el colegio";
            case GRADE_LEVELS -> a.getGradeLevels().stream()
                    .sorted(Comparator.comparingInt(GradeLevel::getSortOrder))
                    .map(GradeLevel::getName).collect(Collectors.joining(", "));
            case COURSES -> a.getCourses().stream()
                    .sorted(Comparator.comparing((Course c) -> c.getGradeLevel().getSortOrder()).thenComparing(Course::getSection))
                    .map(Course::displayName).collect(Collectors.joining(", "));
        };
    }

    /** Ids de destinatarios para el formulario. */
    public static Set<Long> ids(Set<? extends BaseEntity> entities) {
        return entities.stream().map(BaseEntity::getId).collect(Collectors.toSet());
    }
}
