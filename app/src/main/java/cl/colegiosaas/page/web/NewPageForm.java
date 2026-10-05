package cl.colegiosaas.page.web;

import cl.colegiosaas.page.PageKind;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewPageForm {

    private String title;

    /** Vacío: se deriva del título. */
    private String slug;

    private PageKind kind = PageKind.CUSTOM;
}
