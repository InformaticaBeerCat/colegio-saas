package cl.colegiosaas.info;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.media.StoredFile;
import cl.colegiosaas.shared.html.HtmlSanitizer;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Información práctica del colegio: preguntas frecuentes (PUB-09), talleres (PUB-06) y útiles,
 * uniforme y minuta (PUB-10).
 */
@Service
public class GeneralInfoService {

    private final FaqCategoryRepository faqCategories;
    private final FaqEntryRepository faqEntries;
    private final WorkshopRepository workshops;
    private final InfoSheetRepository sheets;
    private final GradeLevelRepository gradeLevels;
    private final AuditTrail audit;

    GeneralInfoService(FaqCategoryRepository faqCategories, FaqEntryRepository faqEntries, WorkshopRepository workshops,
                       InfoSheetRepository sheets, GradeLevelRepository gradeLevels, AuditTrail audit) {
        this.faqCategories = faqCategories;
        this.faqEntries = faqEntries;
        this.workshops = workshops;
        this.sheets = sheets;
        this.gradeLevels = gradeLevels;
        this.audit = audit;
    }

    // --- Preguntas frecuentes ---

    @Transactional(readOnly = true)
    public List<FaqCategory> faqCategories() {
        return faqCategories.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<FaqEntry> allFaqEntries() {
        return faqEntries.findAllWithCategory();
    }

    @Transactional(readOnly = true)
    public List<FaqEntry> publishedFaq() {
        return faqEntries.findByPublishedTrueOrderByCategory_SortOrderAscSortOrderAsc();
    }

    @Transactional(readOnly = true)
    public FaqEntry faqEntry(long id) {
        return faqEntries.findWithCategoryById(id).orElseThrow(() -> new NotFound("La pregunta no existe"));
    }

    @Transactional
    public FaqCategory addFaqCategory(String name) {
        requireText(name, 80, "La categoría necesita un nombre de hasta 80 caracteres");
        int order = faqCategories.findAllByOrderBySortOrderAsc().stream().mapToInt(FaqCategory::getSortOrder).max().orElse(0) + 1;
        FaqCategory category = faqCategories.save(new FaqCategory(name.strip(), order));
        audit.record(AuditAction.CREATE, "FaqCategory", category.getId(), category.getName());
        return category;
    }

    @Transactional
    public void deleteFaqCategory(long id) {
        FaqCategory category = faqCategories.findById(id).orElseThrow(() -> new NotFound("La categoría no existe"));
        try {
            faqCategories.delete(category);
            faqCategories.flush();
        } catch (DataIntegrityViolationException e) {
            throw new RuleViolation("La categoría tiene preguntas; muévelas o elimínalas primero");
        }
        audit.record(AuditAction.DELETE, "FaqCategory", id, category.getName());
    }

    /** Crea ({@code id} nulo) o actualiza una pregunta. La respuesta admite HTML básico, saneado. */
    @Transactional
    public FaqEntry saveFaqEntry(Long id, long categoryId, String question, String answer, boolean published) {
        requireText(question, 300, "La pregunta admite hasta 300 caracteres");
        String cleanAnswer = HtmlSanitizer.richText(answer);
        if (cleanAnswer.isBlank() || cleanAnswer.length() > 4000) {
            throw new RuleViolation("La respuesta es obligatoria y admite hasta 4000 caracteres");
        }
        FaqCategory category = faqCategories.findById(categoryId).orElseThrow(() -> new RuleViolation("Elige una categoría"));
        FaqEntry entry;
        if (id == null) {
            int order = faqEntries.findByCategoryAndPublishedTrueOrderBySortOrderAsc(category).size() + 1;
            entry = faqEntries.save(new FaqEntry(category, question.strip(), cleanAnswer, order));
            audit.record(AuditAction.CREATE, "FaqEntry", entry.getId(), entry.getQuestion());
        } else {
            entry = faqEntry(id);
            entry.setCategory(category);
            entry.setQuestion(question.strip());
            entry.setAnswer(cleanAnswer);
            audit.record(AuditAction.UPDATE, "FaqEntry", id, entry.getQuestion());
        }
        entry.setPublished(published);
        return entry;
    }

    @Transactional
    public void deleteFaqEntry(long id) {
        FaqEntry entry = faqEntry(id);
        faqEntries.delete(entry);
        audit.record(AuditAction.DELETE, "FaqEntry", id, entry.getQuestion());
    }

    // --- Talleres ---

    @Transactional(readOnly = true)
    public List<Workshop> workshops() {
        return workshops.findAllWithLevels().stream()
                .sorted(Comparator.comparingInt(Workshop::getAcademicYear).reversed().thenComparing(Workshop::getName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Workshop> activeWorkshops(int academicYear) {
        return workshops.findByAcademicYearAndActiveTrueOrderByNameAsc(academicYear);
    }

    @Transactional(readOnly = true)
    public Workshop workshop(long id) {
        return workshops.findWithLevelsById(id).orElseThrow(() -> new NotFound("El taller no existe"));
    }

    @Transactional
    public Workshop saveWorkshop(Long id, WorkshopDraft draft) {
        requireText(draft.name(), 120, "El taller necesita un nombre de hasta 120 caracteres");
        if (draft.capacity() != null && draft.capacity() < 1) {
            throw new RuleViolation("Los cupos deben ser al menos 1");
        }
        Workshop workshop = id == null ? workshops.save(new Workshop(draft.name().strip(), draft.academicYear())) : workshop(id);
        workshop.setName(draft.name().strip());
        workshop.setDescription(limit(draft.description(), 2000));
        workshop.setSchedule(limit(draft.schedule(), 200));
        workshop.setInstructor(limit(draft.instructor(), 120));
        workshop.setCapacity(draft.capacity());
        workshop.setAcademicYear(draft.academicYear());
        workshop.setActive(draft.active());
        workshop.targetGradeLevels(new HashSet<>(gradeLevels.findAllById(draft.gradeLevelIds())));
        audit.record(id == null ? AuditAction.CREATE : AuditAction.UPDATE, "Workshop", workshop.getId(), workshop.getName());
        return workshop;
    }

    @Transactional
    public void deleteWorkshop(long id) {
        Workshop workshop = workshop(id);
        workshops.delete(workshop);
        audit.record(AuditAction.DELETE, "Workshop", id, workshop.getName());
    }

    // --- Útiles, uniforme y minuta ---

    @Transactional(readOnly = true)
    public List<InfoSheet> sheets() {
        return sheets.findAllWithDetails().stream()
                .sorted(Comparator.comparing(InfoSheet::getKind).thenComparing(InfoSheet::getAcademicYear, Comparator.reverseOrder())
                        .thenComparing(InfoSheet::getTitle))
                .toList();
    }

    /** Las publicadas y vigentes hoy, ordenadas por tipo y nivel. */
    @Transactional(readOnly = true)
    public List<InfoSheet> currentSheets(LocalDate today) {
        return sheets.findAllWithDetails().stream()
                .filter(s -> s.isCurrentOn(today))
                .sorted(Comparator.comparing(InfoSheet::getKind)
                        .thenComparing(InfoSheet::getAcademicYear, Comparator.reverseOrder())
                        .thenComparing(s -> s.getGradeLevel() == null ? -1 : s.getGradeLevel().getSortOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InfoSheet sheet(long id) {
        return sheets.findWithDetailsById(id).orElseThrow(() -> new NotFound("La ficha no existe"));
    }

    @Transactional
    public InfoSheet saveSheet(Long id, InfoSheetDraft draft) {
        requireText(draft.title(), 200, "La ficha necesita un título de hasta 200 caracteres");
        if (draft.kind() == null) {
            throw new RuleViolation("Elige el tipo");
        }
        if (draft.validFrom() != null && draft.validUntil() != null && draft.validUntil().isBefore(draft.validFrom())) {
            throw new RuleViolation("La vigencia no puede terminar antes de empezar");
        }
        InfoSheet sheet = id == null ? sheets.save(new InfoSheet(draft.kind(), draft.title().strip(), draft.academicYear())) : sheet(id);
        sheet.setKind(draft.kind());
        sheet.setTitle(draft.title().strip());
        sheet.setAcademicYear(draft.academicYear());
        GradeLevel level = draft.gradeLevelId() == null ? null
                : gradeLevels.findById(draft.gradeLevelId()).orElseThrow(() -> new RuleViolation("El nivel no existe"));
        sheet.setGradeLevel(level);
        String content = HtmlSanitizer.richText(draft.content());
        sheet.setContent(content.isBlank() ? null : content);
        sheet.setValidFrom(draft.validFrom());
        sheet.setValidUntil(draft.validUntil());
        audit.record(id == null ? AuditAction.CREATE : AuditAction.UPDATE, "InfoSheet", sheet.getId(), sheet.getTitle());
        return sheet;
    }

    @Transactional
    public void attachSheetFile(long id, StoredFile file) {
        sheet(id).setFile(file);
    }

    @Transactional
    public void removeSheetFile(long id) {
        sheet(id).setFile(null);
    }

    /** Publicar exige algo que mostrar: el texto web o el PDF (o ambos). */
    @Transactional
    public void publishSheet(long id, boolean published) {
        InfoSheet sheet = sheet(id);
        if (published && sheet.getContent() == null && sheet.getFile() == null) {
            throw new RuleViolation("Escribe el contenido o adjunta el PDF antes de publicar");
        }
        sheet.setPublished(published);
        audit.record(published ? AuditAction.PUBLISH : AuditAction.UNPUBLISH, "InfoSheet", id, sheet.getTitle());
    }

    @Transactional
    public void deleteSheet(long id) {
        InfoSheet sheet = sheet(id);
        sheets.delete(sheet);
        audit.record(AuditAction.DELETE, "InfoSheet", id, sheet.getTitle());
    }

    private static void requireText(String value, int max, String message) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw new RuleViolation(message);
        }
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new RuleViolation("Un campo supera los " + max + " caracteres");
        }
        return value.strip();
    }
}
