package cl.colegiosaas.page.web;

import cl.colegiosaas.page.MenuItem;
import cl.colegiosaas.page.MenuLocation;
import cl.colegiosaas.page.MenuService;
import cl.colegiosaas.page.PageException;
import cl.colegiosaas.page.PageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Menú principal y del pie (CFG-05). */
@Controller
@RequestMapping("/admin/menus")
@PreAuthorize("hasAuthority('PAGES')")
class MenuAdminController {

    private final MenuService menus;
    private final PageService pages;

    MenuAdminController(MenuService menus, PageService pages) {
        this.menus = menus;
        this.pages = pages;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "HEADER") MenuLocation menu, Model model) {
        MenuForm form = new MenuForm();
        form.setMenu(menu);
        return show(form, model);
    }

    @PostMapping
    String add(@ModelAttribute("form") MenuForm form, Model model, RedirectAttributes redirect) {
        try {
            menus.add(form.getMenu(), form.toTarget());
            redirect.addFlashAttribute("notice", "Entrada agregada");
            return "redirect:/admin/menus?menu=" + form.getMenu();
        } catch (PageException e) {
            model.addAttribute("problem", e.getMessage());
            return show(form, model);
        }
    }

    @GetMapping("/{id}")
    String edit(@PathVariable long id, Model model) {
        MenuItem item = menus.get(id);
        return showEdit(item, MenuForm.of(item), model);
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @ModelAttribute("form") MenuForm form, Model model, RedirectAttributes redirect) {
        MenuItem item = menus.get(id);
        try {
            menus.update(id, form.toTarget());
            redirect.addFlashAttribute("notice", "Entrada guardada");
            return "redirect:/admin/menus?menu=" + item.getMenu();
        } catch (PageException e) {
            model.addAttribute("problem", e.getMessage());
            return showEdit(item, form, model);
        }
    }

    @PostMapping("/{id}/move")
    String move(@PathVariable long id, @RequestParam int direction) {
        MenuLocation menu = menus.get(id).getMenu();
        menus.move(id, direction);
        return "redirect:/admin/menus?menu=" + menu;
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        MenuLocation menu = menus.get(id).getMenu();
        menus.delete(id);
        redirect.addFlashAttribute("notice", "Entrada eliminada");
        return "redirect:/admin/menus?menu=" + menu;
    }

    private String show(MenuForm form, Model model) {
        model.addAttribute("menu", form.getMenu());
        model.addAttribute("menus", MenuLocation.values());
        model.addAttribute("items", menus.items(form.getMenu()));
        model.addAttribute("form", form);
        model.addAttribute("pages", pages.list());
        model.addAttribute("parents", menus.topLevel(form.getMenu()));
        return "admin/menus/list";
    }

    private String showEdit(MenuItem item, MenuForm form, Model model) {
        List<MenuItem> parents = menus.topLevel(item.getMenu()).stream().filter(p -> !p.getId().equals(item.getId())).toList();
        model.addAttribute("item", item);
        model.addAttribute("form", form);
        model.addAttribute("pages", pages.list());
        model.addAttribute("parents", parents);
        return "admin/menus/edit";
    }
}
