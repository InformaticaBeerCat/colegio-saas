package cl.colegiosaas.news;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.media.MediaAsset;
import cl.colegiosaas.media.MediaAssetRepository;
import cl.colegiosaas.media.MediaKind;
import cl.colegiosaas.page.SeoMetadata;
import cl.colegiosaas.shared.html.HtmlSanitizer;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.text.Slugs;
import cl.colegiosaas.shared.web.AppProperties;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import cl.colegiosaas.site.SiteSection;
import cl.colegiosaas.structure.GradeLevel;
import cl.colegiosaas.structure.GradeLevelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Noticias (NOT-01) con aprobación (NOT-02): quien escribe envía a revisión; quien tiene NEWS_PUBLISH
 * aprueba, publicando de inmediato o programando. Los editores satélite (PUB-07) solo escriben en su sección.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final NewsArticleRepository articles;
    private final NewsCategoryRepository categories;
    private final GradeLevelRepository gradeLevels;
    private final UserAccountRepository users;
    private final MediaAssetRepository assets;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final AppProperties app;
    private final Clock clock;

    NewsService(NewsArticleRepository articles, NewsCategoryRepository categories, GradeLevelRepository gradeLevels,
                UserAccountRepository users, MediaAssetRepository assets, AuditTrail audit, ApplicationEventPublisher events,
                AppProperties app, Clock clock) {
        this.articles = articles;
        this.categories = categories;
        this.gradeLevels = gradeLevels;
        this.users = users;
        this.assets = assets;
        this.audit = audit;
        this.events = events;
        this.app = app;
        this.clock = clock;
    }

    /**
     * Secciones donde la persona puede escribir. Quien aprueba noticias, todas; un editor satélite, solo
     * la suya; el resto del equipo, el sitio principal y exalumnos.
     */
    public static Set<SiteSection> writableSections(UserAccount user) {
        if (user.can(Permission.NEWS_PUBLISH)) {
            return EnumSet.allOf(SiteSection.class);
        }
        Set<SiteSection> sections = EnumSet.noneOf(SiteSection.class);
        if (user.can(Permission.SECTION_PARENTS_CENTER)) {
            sections.add(SiteSection.PARENTS_CENTER);
        }
        if (user.can(Permission.SECTION_STUDENT_COUNCIL)) {
            sections.add(SiteSection.STUDENT_COUNCIL);
        }
        if (sections.isEmpty() && user.can(Permission.NEWS_EDIT)) {
            sections.add(SiteSection.MAIN);
            sections.add(SiteSection.ALUMNI);
        }
        return sections;
    }

    // --- Panel ---

    @Transactional(readOnly = true)
    public Set<SiteSection> sectionsFor(long userId) {
        return writableSections(user(userId));
    }

    /** Noticias de las secciones de la persona: primero las que esperan revisión, luego por fecha de edición. */
    @Transactional(readOnly = true)
    public List<NewsArticle> listFor(long userId) {
        Set<SiteSection> sections = writableSections(user(userId));
        return articles.findAll().stream()
                .filter(a -> sections.contains(a.getSection()))
                .sorted(Comparator.comparing((NewsArticle a) -> a.getStatus() != NewsStatus.IN_REVIEW)
                        .thenComparing(NewsArticle::getUpdatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public NewsArticle getFor(long id, long userId) {
        NewsArticle article = articles.findWithDetailsById(id).orElseThrow(() -> new NotFound("La noticia no existe"));
        if (!writableSections(user(userId)).contains(article.getSection())) {
            throw new NotFound("La noticia no existe");
        }
        return article;
    }

    @Transactional(readOnly = true)
    public long pendingReviewCount() {
        return articles.findByStatusOrderByUpdatedAtAsc(NewsStatus.IN_REVIEW).size();
    }

    @Transactional
    public NewsArticle create(String title, SiteSection section, long userId) {
        UserAccount author = user(userId);
        SiteSection target = section == null ? defaultSection(author) : section;
        requireSection(author, target);
        if (title == null || title.isBlank()) {
            throw new RuleViolation("La noticia necesita un título");
        }
        NewsArticle article = new NewsArticle(Slugs.unique(title, "noticia", articles::existsBySlug), title.strip(), author);
        article.setSection(target);
        articles.save(article);
        audit.record(AuditAction.CREATE, "NewsArticle", article.getId(), article.getTitle());
        return article;
    }

    /**
     * Guarda el contenido. Quien no aprueba noticias solo edita borradores: así nada cambia en el sitio
     * sin pasar por revisión.
     */
    @Transactional
    public void update(long id, NewsDraft draft, long userId) {
        UserAccount editor = user(userId);
        NewsArticle article = getFor(id, userId);
        if (!editor.can(Permission.NEWS_PUBLISH) && article.getStatus() != NewsStatus.DRAFT) {
            throw new RuleViolation("La noticia ya se envió a revisión; pide que te la devuelvan para editarla");
        }
        if (draft.title() == null || draft.title().isBlank()) {
            throw new RuleViolation("La noticia necesita un título");
        }
        SiteSection section = draft.section() == null ? article.getSection() : draft.section();
        requireSection(editor, section);

        article.setTitle(draft.title().strip());
        article.setSlug(checkSlug(draft.slug(), article));
        article.setSummary(blankToNull(draft.summary()));
        article.setBody(HtmlSanitizer.richText(draft.body()));
        article.setSection(section);
        article.setCategory(draft.categoryId() == null ? null
                : categories.findById(draft.categoryId()).orElseThrow(() -> new RuleViolation("La categoría no existe")));
        Set<GradeLevel> levels = new HashSet<>(gradeLevels.findAllById(draft.gradeLevelIds()));
        article.getGradeLevels().stream().filter(l -> !levels.contains(l)).toList().forEach(article::untagGradeLevel);
        levels.forEach(article::tagGradeLevel);
        String metaTitle = blankToNull(draft.metaTitle());
        String metaDescription = blankToNull(draft.metaDescription());
        article.setSeo(metaTitle == null && metaDescription == null ? null : new SeoMetadata(metaTitle, metaDescription));
        article.setFeaturedImage(featuredImage(draft.featuredImageId()));
        audit.record(AuditAction.UPDATE, "NewsArticle", id, article.getTitle());
    }

    /** Envía a revisión y avisa por correo a quienes pueden aprobar. */
    @Transactional
    public void submitForReview(long id, long userId) {
        NewsArticle article = getFor(id, userId);
        try {
            article.submitForReview();
        } catch (IllegalStateException e) {
            throw new RuleViolation("Solo un borrador se envía a revisión");
        }
        audit.record(AuditAction.UPDATE, "NewsArticle", id, "Enviada a revisión: " + article.getTitle());
        String link = app.url("/admin/news/" + id);
        users.findAllByOrderByNameAsc().stream()
                .filter(u -> u.canLogIn() && u.can(Permission.NEWS_PUBLISH) && u.getId() != userId)
                .forEach(u -> events.publishEvent(new OutgoingMail(u.getEmail(), "Noticia para revisar: " + article.getTitle(), """
                        Hola %s:

                        Hay una noticia esperando tu revisión: "%s".
                        Revísala en %s
                        """.formatted(u.getName(), article.getTitle(), link))));
    }

    /**
     * Aprueba y publica (o programa si {@code publishAt} es futuro). Quien puede aprobar también publica
     * directo un borrador propio: el envío a revisión queda implícito.
     */
    @Transactional
    public void approve(long id, Instant publishAt, long userId) {
        UserAccount approver = requirePublisher(userId);
        NewsArticle article = getFor(id, userId);
        if (article.getStatus() == NewsStatus.DRAFT) {
            article.submitForReview();
        }
        if (article.getStatus() != NewsStatus.IN_REVIEW) {
            throw new RuleViolation("La noticia no está esperando aprobación");
        }
        article.approve(approver, publishAt, clock.instant());
        audit.record(AuditAction.PUBLISH, "NewsArticle", id,
                (article.getStatus() == NewsStatus.SCHEDULED ? "Programada: " : "Publicada: ") + article.getTitle());
    }

    /** Devuelve a borrador con un comentario para quien la escribió. */
    @Transactional
    public void returnToDraft(long id, String note, long userId) {
        UserAccount approver = requirePublisher(userId);
        NewsArticle article = getFor(id, userId);
        try {
            article.returnToDraft(approver, blankToNull(note));
        } catch (IllegalStateException e) {
            throw new RuleViolation(e.getMessage());
        }
        audit.record(AuditAction.REJECT, "NewsArticle", id, article.getTitle());
        UserAccount author = article.getAuthor();
        if (!author.getId().equals(approver.getId()) && author.canLogIn()) {
            events.publishEvent(new OutgoingMail(author.getEmail(), "Tu noticia volvió a borrador: " + article.getTitle(), """
                    Hola %s:

                    %s devolvió tu noticia "%s" para que la ajustes.%s

                    Edítala en %s
                    """.formatted(author.getName(), approver.getName(), article.getTitle(),
                    note == null || note.isBlank() ? "" : "\n\nComentario: " + note.strip(),
                    app.url("/admin/news/" + id))));
        }
    }

    /** Saca la noticia del sitio sin borrarla. */
    @Transactional
    public void archive(long id, long userId) {
        requirePublisher(userId);
        NewsArticle article = getFor(id, userId);
        article.archive();
        audit.record(AuditAction.UNPUBLISH, "NewsArticle", id, article.getTitle());
    }

    /** Solo se borra lo que nunca se publicó; lo publicado se archiva. */
    @Transactional
    public void delete(long id, long userId) {
        NewsArticle article = getFor(id, userId);
        if (article.getPublishedAt() != null || article.getStatus() != NewsStatus.DRAFT) {
            throw new RuleViolation("Solo se eliminan borradores que nunca se publicaron; las demás se archivan");
        }
        articles.delete(article);
        audit.record(AuditAction.DELETE, "NewsArticle", id, article.getTitle());
    }

    /** Pasa a publicadas las programadas cuya hora llegó (mientras tanto ya se ven: ver {@link NewsArticle#isVisibleAt}). */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    @Transactional
    public void publishDue() {
        List<NewsArticle> due = articles.findByStatusAndPublishAtLessThanEqual(NewsStatus.SCHEDULED, clock.instant());
        due.forEach(article -> {
            article.publishIfDue(clock.instant());
            audit.recordSystem(AuditAction.PUBLISH, "NewsArticle", article.getId(), "Publicación programada: " + article.getTitle());
        });
        if (!due.isEmpty()) {
            log.info("Noticias programadas publicadas: {}", due.size());
        }
    }

    // --- Sitio público ---

    @Transactional(readOnly = true)
    public Page<NewsArticle> visible(Long categoryId, Long gradeLevelId, Pageable page) {
        return articles.findVisible(clock.instant(), categoryId, gradeLevelId, page);
    }

    @Transactional(readOnly = true)
    public NewsArticle visibleBySlug(String slug) {
        return articles.findBySlug(slug)
                .filter(a -> a.isVisibleAt(clock.instant()))
                .orElseThrow(() -> new NotFound("La noticia no existe"));
    }

    /** Solo fotos aprobadas o exentas: una noticia no puede ser la puerta de entrada de una foto sin revisar. */
    private MediaAsset featuredImage(Long assetId) {
        if (assetId == null) {
            return null;
        }
        MediaAsset asset = assets.findById(assetId).orElseThrow(() -> new RuleViolation("La foto elegida no existe"));
        if (asset.getKind() != MediaKind.IMAGE || !asset.isDisplayable()) {
            throw new RuleViolation("La imagen destacada tiene que ser una foto aprobada");
        }
        return asset;
    }

    private String checkSlug(String slug, NewsArticle article) {
        String clean = slug == null || slug.isBlank() ? article.getSlug() : slug.strip();
        if (!Slugs.isValid(clean)) {
            throw new RuleViolation("La dirección solo admite minúsculas, números y guiones");
        }
        if (articles.findBySlug(clean).filter(other -> other != article).isPresent()) {
            throw new RuleViolation("Ya existe una noticia con la dirección " + clean);
        }
        return clean;
    }

    private static SiteSection defaultSection(UserAccount user) {
        Set<SiteSection> sections = writableSections(user);
        return sections.contains(SiteSection.MAIN) ? SiteSection.MAIN : sections.stream().findFirst()
                .orElseThrow(() -> new RuleViolation("No tienes permiso para escribir noticias"));
    }

    private static void requireSection(UserAccount user, SiteSection section) {
        if (!writableSections(user).contains(section)) {
            throw new RuleViolation("No puedes publicar en esa sección del sitio");
        }
    }

    private UserAccount requirePublisher(long userId) {
        UserAccount user = user(userId);
        if (!user.can(Permission.NEWS_PUBLISH)) {
            throw new RuleViolation("Solo quien aprueba noticias puede hacer esto");
        }
        return user;
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow(() -> new NotFound("La cuenta no existe"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
