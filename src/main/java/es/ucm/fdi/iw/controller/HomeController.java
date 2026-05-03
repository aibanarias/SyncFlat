package es.ucm.fdi.iw.controller;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import es.ucm.fdi.iw.model.MiembroPiso;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.RolPiso;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.service.AlertaService;
import es.ucm.fdi.iw.service.CalendarioService;
import es.ucm.fdi.iw.service.GastoService;
import es.ucm.fdi.iw.service.PisoService;
import es.ucm.fdi.iw.service.TareaService;
import jakarta.servlet.http.HttpSession;

/**
 * Controlador del módulo Home.
 * <p>
 * Actúa como orquestador del dashboard: resuelve el contexto del usuario,
 * delega cada consulta a su service correspondiente y compone el modelo
 * para la vista. No contiene queries JPQL ni lógica de dominio propia.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/modulos/home")
public class HomeController extends BaseController {

    private static final Logger log = LogManager.getLogger(HomeController.class);

    private final PisoService pisoService;
    private final AlertaService alertaService;
    private final CalendarioService calendarioService;
    private final TareaService tareaService;
    private final GastoService gastoService;

    @GetMapping
    public String home(Model model, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null) {
            return "redirect:/modulos/piso";
        }
        User u = (User) session.getAttribute("u");

        model.addAttribute("piso",            piso);
        model.addAttribute("eventos",         calendarioService.obtenerProximosEventos(piso.getId(), 5));
        model.addAttribute("tareasPendientes", tareaService.obtenerPendientesResumen(piso.getId(), 5));
        model.addAttribute("gastos",          gastoService.obtenerRecientes(piso.getId(), 5));
        model.addAttribute("alertas",         alertaService.obtenerNoLeidas(piso.getId()));

        List<MiembroPiso> miembros = pisoService.obtenerMiembrosActivos(piso.getId());
        model.addAttribute("miembros", miembros);

        MiembroPiso miMembresia = pisoService.obtenerMembresia(piso.getId(), u.getId());
        model.addAttribute("miMembresia", miMembresia);
        model.addAttribute("esAdmin", miMembresia != null && miMembresia.getRolEnPiso() == RolPiso.ADMIN);

        int ocupadas = pisoService.contarHabitacionesOcupadas(miembros);
        model.addAttribute("habitacionesOcupadas", ocupadas);

        if (piso.getNumHabitaciones() != null) {
            model.addAttribute("habitacionesLibres",
                Math.max(0, piso.getNumHabitaciones() - ocupadas));
            model.addAttribute("ocupacionHabitaciones",
                pisoService.construirMapaOcupacion(piso, miembros).entrySet());
        }

        return "home";
    }
}
