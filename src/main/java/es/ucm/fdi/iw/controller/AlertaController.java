package es.ucm.fdi.iw.controller;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.ucm.fdi.iw.dto.ApiResponse;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.service.AlertaService;
import es.ucm.fdi.iw.service.PisoService;
import jakarta.servlet.http.HttpSession;

/**
 * Gestiona las acciones sobre las alertas de piso.
 * <p>
 * Marcar leída ({@code /{id}/leer}) usa AJAX; marcar todas ({@code /leer-todas}) usa
 * formulario con redirección.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/modulos/alertas")
public class AlertaController extends BaseController {

    private static final Logger log = LogManager.getLogger(AlertaController.class);

    private final AlertaService alertaService;
    private final PisoService pisoService;

    /**
     * Marca una alerta como leída.
     * Devuelve {@code { ok, message, data: { leida: true } }}.
     * Las excepciones de negocio son gestionadas por {@link GlobalExceptionHandler}.
     */
    @PostMapping("/{id}/leer")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> marcarLeida(
            @PathVariable long id, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        alertaService.marcarLeida(id, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("leida", true)));
    }

    /**
     * Marca como leídas todas las alertas pendientes del piso.
     * Redirige a {@code /modulos/home} con un mensaje flash informativo.
     */
    @PostMapping("/leer-todas")
    public String marcarTodasLeidas(HttpSession session, RedirectAttributes ra) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null) return "redirect:/login";
        int n = alertaService.marcarTodasLeidas(piso.getId());
        ra.addFlashAttribute("flashSuccess",
            n + " alerta" + (n == 1 ? "" : "s") + " marcadas como leídas.");
        return "redirect:/modulos/home";
    }
}
