package cl.colegiosaas.publicsite;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Portada pública provisoria: en la fase 3 se reemplaza por la página armada con bloques. */
@Controller
class HomeController {

    @GetMapping("/")
    String home() {
        return "public/home";
    }
}
