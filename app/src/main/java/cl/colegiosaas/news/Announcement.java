package cl.colegiosaas.news;

import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.shared.persistence.BaseEntity;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.GradeLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Comunicado o circular con fecha y destinatarios (NOT-03). */
@Entity
@Table(name = "announcement")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseEntity {

    @NotBlank
    private String title;

    /** Columna LONGTEXT. */
    private String body = "";

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private AnnouncementAudience audience = AnnouncementAudience.EVERYONE;

    @ManyToMany
    @JoinTable(name = "announcement_grade_level",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "grade_level_id"))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<GradeLevel> gradeLevels = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "announcement_course",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<Course> courses = new HashSet<>();

    /** Solo visible con sesión en la zona comunidad (ZON-02, v2). */
    private boolean communityOnly;

    /** PDF de la circular. */
    @ManyToOne(fetch = FetchType.LAZY)
    private StoredFile attachment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Setter(AccessLevel.NONE)
    private UserAccount author;

    @Setter(AccessLevel.NONE)
    private Instant publishedAt;

    public Announcement(String title, UserAccount author) {
        this.title = title;
        this.author = Objects.requireNonNull(author, "author");
    }

    public void addressToEveryone() {
        audience = AnnouncementAudience.EVERYONE;
        gradeLevels.clear();
        courses.clear();
    }

    public void addressToGradeLevels(Set<GradeLevel> levels) {
        requireNotEmpty(levels);
        audience = AnnouncementAudience.GRADE_LEVELS;
        courses.clear();
        gradeLevels.clear();
        gradeLevels.addAll(levels);
    }

    public void addressToCourses(Set<Course> targetCourses) {
        requireNotEmpty(targetCourses);
        audience = AnnouncementAudience.COURSES;
        gradeLevels.clear();
        courses.clear();
        courses.addAll(targetCourses);
    }

    public Set<GradeLevel> getGradeLevels() {
        return Collections.unmodifiableSet(gradeLevels);
    }

    public Set<Course> getCourses() {
        return Collections.unmodifiableSet(courses);
    }

    public void publish() {
        publishedAt = Instant.now();
    }

    public void unpublish() {
        publishedAt = null;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    private static void requireNotEmpty(Set<?> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("Indica al menos un destinatario");
        }
    }
}
