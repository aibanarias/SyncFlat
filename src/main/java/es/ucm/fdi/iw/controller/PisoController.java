package es.ucm.fdi.iw.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.ucm.fdi.iw.dto.ConfigurarHabitacionesForm;
import es.ucm.fdi.iw.dto.CrearPisoForm;
import es.ucm.fdi.iw.dto.HabitacionForm;
import es.ucm.fdi.iw.dto.UnirseForm;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.service.PisoService;
import es.ucm.fdi.iw.service.PisoService.AbandonoResultado;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Gestiona el ciclo de vida completo de la pertenencia a un piso:
 * selección, creación, adhesión, habitaciones, administración y abandono.
 * <p>
 * Toda la lógica de negocio vive en {@link PisoService}. Este controlador
 * valida la entrada, delega y aplica el patrón PRG (Post-Redirect-Get).
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/modulos/piso")
public class PisoController extends BaseController {

    private static final Logger log = LogManager.getLogger(PisoController.class);

    private final PisoService pisoService;

    /** Muestra la pantalla de selección. Redirige a home si el usuario ya tiene piso. */
    @GetMapping
    public String piso(Model model, HttpSession session) {
        if (pisoService.resolverPiso(session) != null) {
            return "redirect:/modulos/home";
        }
        return "piso";
    }

    /** Crea un nuevo piso; el creador queda automáticamente como ADMIN. */
    @PostMapping("/crear")
    public String crear(@Valid @ModelAttribute CrearPisoForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/modulos/piso";
        }
        try {
            pisoService.crearPiso(form, u);
            ra.addFlashAttribute("flashSuccess", "Piso creado correctamente. ¡Bienvenido, administrador!");
            return "redirect:/modulos/home";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/modulos/piso";
        }
    }

    /** Une al usuario al piso identificado por el código de invitación. */
    @PostMapping("/unirse")
    public String unirse(@Valid @ModelAttribute UnirseForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/modulos/piso";
        }
        try {
            pisoService.unirse(form.getCodigo(), u);
            ra.addFlashAttribute("flashSuccess", "Te has unido al piso correctamente. ¡Bienvenido!");
            return "redirect:/modulos/home";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/modulos/piso";
        }
    }

    /**
     * Asigna la habitación del usuario.
     * Valida que no esté ya ocupada y que esté dentro del rango configurado.
     */
    @PostMapping("/habitaciones/mi-habitacion")
    public String asignarHabitacion(@Valid @ModelAttribute HabitacionForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/modulos/home";
        }
        try {
            pisoService.asignarHabitacion(u, form.getNumHabitacion());
            ra.addFlashAttribute("flashSuccess", "Habitación " + form.getNumHabitacion() + " asignada correctamente.");
            return "redirect:/modulos/home";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/modulos/home";
        }
    }

    /**
     * Configura el número total de habitaciones del piso.
     * Solo los ADMIN del piso pueden realizar esta acción.
     */
    @PostMapping("/habitaciones/configurar")
    public String configurarHabitaciones(@Valid @ModelAttribute ConfigurarHabitacionesForm form,
            BindingResult errors, HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/modulos/home";
        }
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null) {
            ra.addFlashAttribute("flashError", "No perteneces a ningún piso activo.");
            return "redirect:/modulos/piso";
        }
        try {
            pisoService.configurarHabitaciones(piso.getId(), form.getNumHabitaciones(), u);
            ra.addFlashAttribute("flashSuccess", "Piso actualizado: " + form.getNumHabitaciones() + " habitaciones en total.");
            return "redirect:/modulos/home";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/modulos/home";
        }
    }

    /**
     * Promueve a un miembro activo del piso al rol ADMIN.
     * Solo los ADMIN actuales pueden realizar esta acción.
     */
    @PostMapping("/promover-admin")
    public String promoverAdmin(@RequestParam long membresiaId,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";
        try {
            pisoService.promoverAdmin(membresiaId, u);
            ra.addFlashAttribute("flashSuccess", "Miembro promovido a administrador del piso correctamente.");
            return "redirect:/modulos/home";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/modulos/home";
        }
    }

    /**
     * Gestiona el abandono del piso.
     * <p>
     * Si el usuario era el único ADMIN, se promueve automáticamente al miembro más antiguo.
     * Si era el último miembro activo, el piso y todos sus datos son eliminados.
     */
    @PostMapping("/abandonar")
    public String abandonar(HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";
        try {
            AbandonoResultado resultado = pisoService.abandonar(u);
            String msg = switch (resultado) {
                case ABANDONADO ->
                    "Has abandonado el piso. Puedes crear o unirte a otro cuando quieras.";
                case ABANDONADO_REASIGNACION ->
                    "Has abandonado el piso. Se ha asignado un nuevo administrador automáticamente.";
                case ABANDONADO_PISO_ELIMINADO ->
                    "Has abandonado el piso. El piso ha sido eliminado al no quedar miembros activos.";
            };
            ra.addFlashAttribute("flashSuccess", msg);
            return "redirect:/modulos/piso";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/modulos/home";
        }
    }
}
