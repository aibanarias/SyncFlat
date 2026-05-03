package es.ucm.fdi.iw.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controlador de rutas públicas de SyncFlat.
 * <p>
 * Gestiona la página de inicio, el formulario de login y la página de autores.
 * Ninguna de estas rutas requiere autenticación.
 */
@Controller
public class RootController extends BaseController {

    private static final Logger log = LogManager.getLogger(RootController.class);

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        boolean error = request.getQueryString() != null && request.getQueryString().contains("error");
        model.addAttribute("loginError", error);
        return "login";
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/autores")
    public String autores() {
        return "autores";
    }
}
