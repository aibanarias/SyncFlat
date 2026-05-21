package es.ucm.fdi.iw.controller;

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
import es.ucm.fdi.iw.dto.TareaForm;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.service.PisoService;
import es.ucm.fdi.iw.service.TareaService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador del módulo de Tareas.
 * <p>
 * Las tareas siguen el flujo: PENDIENTE → COMPLETADA → VALIDADA.
 * Toda la lógica de negocio vive en {@link TareaService}.
 * Las excepciones de negocio ({@link IllegalArgumentException},
 * {@link IllegalStateException}, {@link SecurityException}) son capturadas
 * por {@link GlobalExceptionHandler} y devueltas como {@code ApiResponse} estándar.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/tareas")
public class TareaController extends BaseController {

    private static final Logger log = LogManager.getLogger(TareaController.class);

    private final PisoService pisoService;
    private final TareaService tareaService;

    /**
     * Vista principal de tareas.
     * Carga las tres listas de estado y el id del usuario actual para que
     * el template pueda controlar qué acciones mostrar a cada miembro.
     */
    @GetMapping
    public String tareas(Model model, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso", piso);
            model.addAttribute("pendientes",          tareaService.obtenerPendientes(piso.getId()));
            model.addAttribute("pendientesDeValidar", tareaService.obtenerPendientesDeValidar(piso.getId()));
            model.addAttribute("validadas",           tareaService.obtenerValidadas(piso.getId()));
            model.addAttribute("sinAsignar",          tareaService.obtenerSinAsignar(piso.getId()));
            model.addAttribute("miembros",            tareaService.obtenerMiembrosActivos(piso.getId()));

            User u = (User) session.getAttribute("u");
            model.addAttribute("usuarioActualId", u != null ? u.getId() : -1L);
        }
        return "tareas";
    }

    /** Crea una nueva tarea y opcionalmente la asigna a un miembro. */
    @PostMapping
    public String crearTarea(@Valid @ModelAttribute TareaForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/tareas";
        }
        tareaService.crearTarea(form, piso);
        ra.addFlashAttribute("flashSuccess", "Tarea creada correctamente.");
        return "redirect:/tareas";
    }

    /**
     * Alterna el estado PENDIENTE ↔ COMPLETADA de una asignación.
     * Devuelve {@code { ok, message, data: { completada, validada } }}.
     * Las excepciones de negocio y seguridad son gestionadas por
     * {@link GlobalExceptionHandler}.
     */
    @PostMapping("/{id}/completar")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> completarTarea(
            @PathVariable long id, HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        Map<String, Object> result = tareaService.completarTarea(id, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Valida una asignación en estado COMPLETADA.
     * Devuelve {@code { ok, message, data: { validada, validador } }}.
     * Las excepciones de negocio y seguridad son gestionadas por
     * {@link GlobalExceptionHandler}.
     */
    @PostMapping("/{id}/validar")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> validarTarea(
            @PathVariable long id, HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        Map<String, Object> result = tareaService.validarTarea(id, u, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
