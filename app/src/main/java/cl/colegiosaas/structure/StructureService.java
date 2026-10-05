package cl.colegiosaas.structure;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Niveles y cursos del colegio: solo estructura, para filtrar calendario, noticias y comunicados.
 * Nada académico (ni notas ni asistencia).
 */
@Service
public class StructureService {

    /** Niveles de la educación chilena, de sala cuna a 4° medio, para cargar en un clic. */
    private static final List<StandardLevel> CHILEAN_LEVELS = List.of(
            new StandardLevel("Sala Cuna", EducationStage.EARLY_CHILDHOOD),
            new StandardLevel("Nivel Medio", EducationStage.EARLY_CHILDHOOD),
            new StandardLevel("Pre-kínder", EducationStage.EARLY_CHILDHOOD),
            new StandardLevel("Kínder", EducationStage.EARLY_CHILDHOOD),
            new StandardLevel("1° Básico", EducationStage.PRIMARY),
            new StandardLevel("2° Básico", EducationStage.PRIMARY),
            new StandardLevel("3° Básico", EducationStage.PRIMARY),
            new StandardLevel("4° Básico", EducationStage.PRIMARY),
            new StandardLevel("5° Básico", EducationStage.PRIMARY),
            new StandardLevel("6° Básico", EducationStage.PRIMARY),
            new StandardLevel("7° Básico", EducationStage.PRIMARY),
            new StandardLevel("8° Básico", EducationStage.PRIMARY),
            new StandardLevel("1° Medio", EducationStage.SECONDARY),
            new StandardLevel("2° Medio", EducationStage.SECONDARY),
            new StandardLevel("3° Medio", EducationStage.SECONDARY),
            new StandardLevel("4° Medio", EducationStage.SECONDARY));

    private final GradeLevelRepository levels;
    private final CourseRepository courses;
    private final AuditTrail audit;

    StructureService(GradeLevelRepository levels, CourseRepository courses, AuditTrail audit) {
        this.levels = levels;
        this.courses = courses;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<GradeLevel> levels() {
        return levels.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Course> courses(int academicYear) {
        return courses.findByAcademicYearOrderByGradeLevel_SortOrderAscSectionAsc(academicYear);
    }

    /** Carga los niveles chilenos que falten, sin duplicar los que el colegio ya creó. */
    @Transactional
    public void loadChileanLevels() {
        List<String> existing = levels.findAll().stream().map(GradeLevel::getName).toList();
        int order = levels.findAll().stream().mapToInt(GradeLevel::getSortOrder).max().orElse(0);
        for (StandardLevel level : CHILEAN_LEVELS) {
            if (!existing.contains(level.name())) {
                levels.save(new GradeLevel(level.name(), level.stage(), null, ++order));
            }
        }
        audit.record(AuditAction.CREATE, "GradeLevel", null, "Niveles de la educación chilena");
    }

    @Transactional
    public GradeLevel addLevel(String name, EducationStage stage) {
        if (name == null || name.isBlank()) {
            throw new RuleViolation("El nivel necesita un nombre");
        }
        if (levels.findAll().stream().anyMatch(l -> l.getName().equalsIgnoreCase(name.strip()))) {
            throw new RuleViolation("Ya existe el nivel " + name.strip());
        }
        int order = levels.findAll().stream().mapToInt(GradeLevel::getSortOrder).max().orElse(0) + 1;
        GradeLevel level = levels.save(new GradeLevel(name.strip(), stage, null, order));
        audit.record(AuditAction.CREATE, "GradeLevel", level.getId(), level.getName());
        return level;
    }

    /** Un nivel con cursos, noticias o eventos no se borra (lo impiden las llaves foráneas). */
    @Transactional
    public void deleteLevel(long id) {
        GradeLevel level = levels.findById(id).orElseThrow(() -> new NotFound("El nivel no existe"));
        if (courses.existsByGradeLevel(level)) {
            throw new RuleViolation("El nivel " + level.getName() + " tiene cursos; elimínalos primero");
        }
        try {
            levels.delete(level);
            levels.flush();
        } catch (DataIntegrityViolationException e) {
            throw new RuleViolation("El nivel " + level.getName() + " tiene cursos o contenido asociado; no se puede eliminar");
        }
        audit.record(AuditAction.DELETE, "GradeLevel", id, level.getName());
    }

    @Transactional
    public Course addCourse(long levelId, String section, int academicYear) {
        GradeLevel level = levels.findById(levelId).orElseThrow(() -> new NotFound("El nivel no existe"));
        String clean = section == null ? "" : section.strip().toUpperCase();
        if (clean.length() > 5) {
            throw new RuleViolation("La letra del curso admite hasta 5 caracteres");
        }
        boolean exists = courses(academicYear).stream()
                .anyMatch(c -> c.getGradeLevel().equals(level) && c.getSection().equals(clean));
        if (exists) {
            throw new RuleViolation("Ese curso ya existe en " + academicYear);
        }
        Course course = courses.save(new Course(level, clean, academicYear));
        audit.record(AuditAction.CREATE, "Course", course.getId(), course.displayName() + " " + academicYear);
        return course;
    }

    @Transactional
    public void deleteCourse(long id) {
        Course course = courses.findById(id).orElseThrow(() -> new NotFound("El curso no existe"));
        try {
            courses.delete(course);
            courses.flush();
        } catch (DataIntegrityViolationException e) {
            throw new RuleViolation("El curso tiene estudiantes o contenido asociado; no se puede eliminar");
        }
        audit.record(AuditAction.DELETE, "Course", id, course.displayName());
    }

    private record StandardLevel(String name, EducationStage stage) {
    }
}
