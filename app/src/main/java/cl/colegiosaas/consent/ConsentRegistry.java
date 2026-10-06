package cl.colegiosaas.consent;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaLibrary;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.privacy.LegalText;
import cl.colegiosaas.privacy.LegalTextKind;
import cl.colegiosaas.privacy.LegalTextRepository;
import cl.colegiosaas.privacy.LegalTextService;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.Course;
import cl.colegiosaas.structure.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Registro de estudiantes y sus autorizaciones de imagen (Ley 21.719 y 21.430): solo nombre y curso,
 * sin RUN (PRV-08). Revocar la autorización para el sitio retira de inmediato todas las fotos donde el
 * estudiante fue etiquetado (MED-09).
 */
@Service
public class ConsentRegistry {

    private final StudentRepository students;
    private final ImageConsentRepository consents;
    private final StudentAppearanceRepository appearances;
    private final CourseRepository courses;
    private final LegalTextRepository legalTexts;
    private final UserAccountRepository users;
    private final MediaLibrary media;
    private final LegalTextService legalTextService;
    private final BlindIndex index;
    private final AuditTrail audit;
    private final Clock clock;

    ConsentRegistry(StudentRepository students, ImageConsentRepository consents, StudentAppearanceRepository appearances,
                    CourseRepository courses, LegalTextRepository legalTexts, UserAccountRepository users,
                    MediaLibrary media, LegalTextService legalTextService, BlindIndex index, AuditTrail audit, Clock clock) {
        this.students = students;
        this.consents = consents;
        this.appearances = appearances;
        this.courses = courses;
        this.legalTexts = legalTexts;
        this.users = users;
        this.media = media;
        this.legalTextService = legalTextService;
        this.index = index;
        this.audit = audit;
        this.clock = clock;
    }

    /** Estudiante con sus autorizaciones vigentes, para la lista del curso. */
    public record StudentRow(long id, String name, long courseId, String course, boolean active, boolean website, boolean socialMedia, boolean print) {
    }

    @Transactional(readOnly = true)
    public List<StudentRow> students(int academicYear) {
        List<Student> list = students.findByCourse_AcademicYear(academicYear);
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> web = consents.studentIdsWithActiveConsent(list, ConsentChannel.WEBSITE);
        List<Long> social = consents.studentIdsWithActiveConsent(list, ConsentChannel.SOCIAL_MEDIA);
        List<Long> print = consents.studentIdsWithActiveConsent(list, ConsentChannel.PRINT);
        return list.stream()
                .sorted(Comparator.comparing((Student s) -> s.getCourse().getGradeLevel().getSortOrder())
                        .thenComparing(s -> s.getCourse().getSection())
                        .thenComparing(Student::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new StudentRow(s.getId(), s.getFullName(), s.getCourse().getId(), s.getCourse().displayName(), s.isActive(),
                        web.contains(s.getId()), social.contains(s.getId()), print.contains(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Student student(long id) {
        return students.findWithCourseById(id).orElseThrow(() -> new NotFound("El estudiante no existe"));
    }

    @Transactional(readOnly = true)
    public List<ImageConsent> history(long studentId) {
        return consents.findByStudentOrderByGrantedAtDesc(student(studentId));
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> photosOf(long studentId) {
        return appearances.findAssetsShowing(student(studentId));
    }

    /** El formulario de autorización vigente; sin él no se pueden registrar autorizaciones. */
    @Transactional(readOnly = true)
    public Optional<LegalText> currentForm() {
        return legalTexts.findFirstByKindAndEffectiveFromIsNotNullOrderByVersionNumberDesc(LegalTextKind.IMAGE_CONSENT_FORM);
    }

    /** Publica la primera versión del formulario desde la plantilla; después se ajusta en "Textos legales". */
    @Transactional
    public LegalText publishBaseForm(long userId) {
        if (currentForm().isPresent()) {
            throw new RuleViolation("Ya hay un formulario de autorización publicado");
        }
        return legalTextService.publishFromTemplate(LegalTextKind.IMAGE_CONSENT_FORM, userId);
    }

    @Transactional
    public Student addStudent(String fullName, long courseId, String guardianEmail) {
        if (fullName == null || fullName.isBlank() || fullName.strip().length() > 150) {
            throw new RuleViolation("Escribe el nombre completo del estudiante (hasta 150 caracteres)");
        }
        String email = guardianEmail == null || guardianEmail.isBlank() ? null : guardianEmail.strip();
        if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new RuleViolation("El correo del apoderado no es válido");
        }
        Course course = courses.findById(courseId).orElseThrow(() -> new RuleViolation("El curso no existe"));
        Student student = students.save(new Student(fullName.strip().replaceAll("\\s+", " "), course, email, index));
        audit.record(AuditAction.CREATE, "Student", student.getId(), course.displayName());
        return student;
    }

    @Transactional
    public void deactivate(long studentId) {
        student(studentId).deactivate();
        audit.record(AuditAction.DEACTIVATE, "Student", studentId, null);
    }

    /** Registra una autorización vigente; si ya había una para ese canal, no se duplica. */
    @Transactional
    public ImageConsent grant(long studentId, ConsentChannel channel, String grantedByName, ConsentMethod method,
                              StoredFile evidence, long userId) {
        Student student = student(studentId);
        LegalText form = currentForm().orElseThrow(() -> new RuleViolation(
                "Publica primero el formulario de autorización de imagen"));
        if (grantedByName == null || grantedByName.isBlank()) {
            throw new RuleViolation("Indica el nombre del apoderado que autorizó");
        }
        if (consents.findFirstByStudentAndChannelAndRevokedAtIsNull(student, channel).isPresent()) {
            throw new RuleViolation("Ya hay una autorización vigente para ese canal");
        }
        ImageConsent consent = consents.save(new ImageConsent(student, channel, grantedByName.strip(), method, form,
                evidence, user(userId), clock.instant()));
        audit.record(AuditAction.CREATE, "ImageConsent", consent.getId(), channel.name());
        return consent;
    }

    /**
     * Revoca la autorización. Si es la del sitio web, todas las fotos donde el estudiante está etiquetado
     * se retiran en el acto (MED-09); devuelve cuántas.
     */
    @Transactional
    public int revoke(long studentId, ConsentChannel channel, String note, long userId) {
        Student student = student(studentId);
        ImageConsent consent = consents.findFirstByStudentAndChannelAndRevokedAtIsNull(student, channel)
                .orElseThrow(() -> new RuleViolation("No hay una autorización vigente para ese canal"));
        consent.revoke(note == null || note.isBlank() ? null : note.strip());
        audit.record(AuditAction.UPDATE, "ImageConsent", consent.getId(), "Revocada: " + channel);
        if (channel != ConsentChannel.WEBSITE) {
            return 0;
        }
        List<MediaAsset> photos = appearances.findAssetsShowing(student).stream().filter(a -> !a.isWithdrawn()).toList();
        photos.forEach(photo -> media.withdraw(photo.getId(), "Autorización de imagen revocada", userId));
        return photos.size();
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow(() -> new NotFound("La cuenta no existe"));
    }
}
