package es.ucm.fdi.iw.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import es.ucm.fdi.iw.dto.ApiResponse;
import es.ucm.fdi.iw.dto.GastoForm;
import es.ucm.fdi.iw.model.ParticipanteGasto;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.service.GastoService;
import es.ucm.fdi.iw.service.PisoService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador del módulo de Gastos.
 * <p>
 * Delega toda la lógica de negocio en {@link GastoService}.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/modulos/gastos")
public class GastoController extends BaseController {

    private static final Logger log = LogManager.getLogger(GastoController.class);

    private final PisoService pisoService;
    private final GastoService gastoService;

    @GetMapping
    public String gastos(Model model, HttpSession session) {
        User u    = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso",      piso);
            model.addAttribute("balances",  gastoService.calcularBalanceNeto(piso.getId()));
            model.addAttribute("miembros",  gastoService.obtenerMiembrosActivos(piso.getId()));
            model.addAttribute("historial", gastoService.obtenerHistorial(piso.getId()));
            if (u != null) {
                List<ParticipanteGasto> loDebo  = gastoService.obtenerLoDebo(piso.getId(), u.getId());
                List<ParticipanteGasto> meDeben = gastoService.obtenerMeDeben(piso.getId(), u.getId());
                BigDecimal totalLoDebo  = loDebo.stream()
                    .map(ParticipanteGasto::getImporteAsignado).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalMeDeben = meDeben.stream()
                    .map(ParticipanteGasto::getImporteAsignado).reduce(BigDecimal.ZERO, BigDecimal::add);
                model.addAttribute("loDebo",        loDebo);
                model.addAttribute("meDeben",       meDeben);
                model.addAttribute("totalLoDebo",   totalLoDebo);
                model.addAttribute("totalMeDeben",  totalMeDeben);
            }
        }
        return "gastos";
    }

    @PostMapping
    public String crearGasto(@Valid @ModelAttribute GastoForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/modulos/gastos";
        }
        gastoService.crearGasto(form, u.getId(), piso);
        ra.addFlashAttribute("flashSuccess", "Gasto registrado correctamente.");
        return "redirect:/modulos/gastos";
    }

    /**
     * Marca la participación del usuario como pagada.
     * Devuelve {@code { ok, message, data: { pagado, liquidado } }}.
     * Las excepciones de negocio y seguridad son gestionadas por {@link GlobalExceptionHandler}.
     */
    @PostMapping("/participante/{id}/pagar")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> pagarParticipacion(
            @PathVariable long id, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        boolean liquidado = gastoService.pagarParticipacion(id, u.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("pagado", true, "liquidado", liquidado)));
    }
}
