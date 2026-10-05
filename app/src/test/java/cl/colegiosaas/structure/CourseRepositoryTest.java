package cl.colegiosaas.structure;

import cl.colegiosaas.support.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class CourseRepositoryTest {

    @Autowired
    GradeLevelRepository levels;

    @Autowired
    CourseRepository courses;

    @Autowired
    TestEntityManager em;

    @Test
    void coursesOfAYearAreListedByLevelThenSection() {
        GradeLevel kinder = levels.save(new GradeLevel("Kínder", EducationStage.EARLY_CHILDHOOD, null, 1));
        GradeLevel firstGrade = levels.save(new GradeLevel("1° Básico", EducationStage.PRIMARY, null, 2));
        courses.save(new Course(firstGrade, "B", 2027));
        courses.save(new Course(firstGrade, "A", 2027));
        courses.save(new Course(kinder, "", 2027));
        courses.save(new Course(firstGrade, "A", 2026));
        em.flush();
        em.clear();

        assertThat(courses.findByAcademicYearOrderByGradeLevel_SortOrderAscSectionAsc(2027))
                .extracting(Course::displayName)
                .containsExactly("Kínder", "1° Básico A", "1° Básico B");
    }

    @Test
    void sameCourseCannotRepeatInAYear() {
        GradeLevel level = levels.save(new GradeLevel("2° Medio", EducationStage.SECONDARY, null, 12));
        courses.saveAndFlush(new Course(level, "A", 2027));

        assertThatThrownBy(() -> courses.saveAndFlush(new Course(level, "A", 2027)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlySecondaryLevelsHaveATrack() {
        GradeLevel thirdTp = new GradeLevel("3° Medio TP", EducationStage.SECONDARY, SecondaryTrack.TECHNICAL_PROFESSIONAL, 13);
        assertThat(thirdTp.getTrack()).isEqualTo(SecondaryTrack.TECHNICAL_PROFESSIONAL);

        assertThatThrownBy(() -> new GradeLevel("4° Básico", EducationStage.PRIMARY, SecondaryTrack.SCIENTIFIC_HUMANISTIC, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
