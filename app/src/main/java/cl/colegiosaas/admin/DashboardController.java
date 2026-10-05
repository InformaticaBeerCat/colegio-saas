package cl.colegiosaas.admin;

import cl.colegiosaas.consent.ImageReview;
import cl.colegiosaas.documents.DocumentService;
import cl.colegiosaas.identity.AccountService;
import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.media.FileScanner;
import cl.colegiosaas.news.NewsService;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.setup.StarterContent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** Portada del panel: la cuenta, los accesos de cada rol y lo que requiere atención. */
@Controller
class DashboardController {

    private final AccountService accounts;
    private final StarterContent starter;
    private final DocumentService documents;
    private final NewsService news;
    private final ImageReview review;
    private final FileScanner scanner;

    DashboardController(AccountService accounts, StarterContent starter, DocumentService documents, NewsService news,
                        ImageReview review, FileScanner scanner) {
        this.accounts = accounts;
        this.starter = starter;
        this.documents = documents;
        this.news = news;
        this.review = review;
        this.scanner = scanner;
    }

    @GetMapping("/admin")
    String dashboard(@AuthenticationPrincipal SchoolUser me, Model model) {
        model.addAttribute("account", accounts.get(me.id()));
        // El asistente de marca se ofrece mientras el sitio no tenga portada (CFG-01).
        model.addAttribute("showWelcome", me.can(Permission.SITE_DESIGN) && me.can(Permission.PAGES) && !starter.exists());
        // DOC-04: alerta de documentos con más de 12 meses sin actualizar.
        model.addAttribute("documentsDue", me.can(Permission.DOCUMENTS) ? documents.dueForReview() : List.of());
        model.addAttribute("newsInReview", me.can(Permission.NEWS_PUBLISH) ? news.pendingReviewCount() : 0L);
        model.addAttribute("photosInReview", me.can(Permission.MEDIA_REVIEW) ? review.queue().size() : 0);
        model.addAttribute("antivirus", scanner.isActive());
        return "admin/dashboard";
    }
}
