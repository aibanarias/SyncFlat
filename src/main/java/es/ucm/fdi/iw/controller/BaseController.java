package es.ucm.fdi.iw.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.servlet.http.HttpSession;

/**
 * Clase base para todos los controladores de SyncFlat.
 * <p>
 * Inyecta en el modelo Thymeleaf los atributos de sesión que los fragmentos
 * (nav, head) necesitan en cada petición: usuario en sesión, URL base,
 * URL del WebSocket y topics suscritos.
 */
public abstract class BaseController {

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }
}
