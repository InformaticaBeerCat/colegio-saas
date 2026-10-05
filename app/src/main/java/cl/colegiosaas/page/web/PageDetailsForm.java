package cl.colegiosaas.page.web;

import cl.colegiosaas.page.Page;
import cl.colegiosaas.page.PageService.PageDetails;
import cl.colegiosaas.site.SiteSection;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageDetailsForm {

    @Size(max = 200)
    private String title;

    @Size(max = 120)
    private String slug;

    private SiteSection section = SiteSection.MAIN;

    @Size(max = 120, message = "Hasta 120 caracteres")
    private String metaTitle;

    @Size(max = 300, message = "Hasta 300 caracteres")
    private String metaDescription;

    private boolean noindex;

    static PageDetailsForm of(Page page) {
        PageDetailsForm form = new PageDetailsForm();
        form.title = page.getTitle();
        form.slug = page.getSlug();
        form.section = page.getSection();
        form.metaTitle = page.getSeo() == null ? null : page.getSeo().metaTitle();
        form.metaDescription = page.getSeo() == null ? null : page.getSeo().metaDescription();
        form.noindex = page.isNoindex();
        return form;
    }

    PageDetails toDetails() {
        return new PageDetails(title, slug, section, metaTitle, metaDescription, noindex);
    }
}
