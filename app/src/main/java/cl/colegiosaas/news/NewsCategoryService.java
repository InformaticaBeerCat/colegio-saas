package cl.colegiosaas.news;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.text.Slugs;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Categorías de noticias: "Deportes", "Actividades", "Logros"… */
@Service
public class NewsCategoryService {

    private final NewsCategoryRepository categories;
    private final AuditTrail audit;

    NewsCategoryService(NewsCategoryRepository categories, AuditTrail audit) {
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NewsCategory> list() {
        return categories.findAllByOrderByNameAsc();
    }

    @Transactional
    public NewsCategory add(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 80) {
            throw new RuleViolation("La categoría necesita un nombre de hasta 80 caracteres");
        }
        String slug = Slugs.unique(name, "categoria", s -> categories.findBySlug(s).isPresent());
        NewsCategory category = categories.save(new NewsCategory(name.strip(), slug));
        audit.record(AuditAction.CREATE, "NewsCategory", category.getId(), category.getName());
        return category;
    }

    @Transactional
    public void delete(long id) {
        NewsCategory category = categories.findById(id).orElseThrow(() -> new NotFound("La categoría no existe"));
        try {
            categories.delete(category);
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            throw new RuleViolation("Hay noticias en esa categoría; cámbiales la categoría antes de eliminarla");
        }
        audit.record(AuditAction.DELETE, "NewsCategory", id, category.getName());
    }
}
