package es.ucm.fdi.iw.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import es.ucm.fdi.iw.dto.ApiResponse;
import es.ucm.fdi.iw.dto.BloqueForm;
import es.ucm.fdi.iw.dto.EventoForm;
import es.ucm.fdi.iw.model.EstadoAsistencia;
import es.ucm.fdi.iw.model.EstadoEvento;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.service.CalendarioService;
import es.ucm.fdi.iw.service.PisoService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Controlador del módulo de Calendario.
 * <p>
 * Los eventos siguen el flujo: PROPUESTO → APROBADO → (asistencia individual)
 * con posibilidad de rechazo en cualquier punto previo a RECHAZADO.
 * <p>
 * Los endpoints AJAX ({@code @ResponseBody}) delegan la gestión de errores de negocio
 * ({@link IllegalArgumentException}, {@link IllegalStateException},
 * {@link SecurityException}) a {@link GlobalExceptionHandler}.
 * Los endpoints MVC de formulario capturan sus propias excepciones con flash messages.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/modulos/calendario")
public class CalendarioController extends BaseController {

    private static final Logger log = LogManager.getLogger(CalendarioController.class);

    private final PisoService pisoService;
    private final CalendarioService calendarioService;

    /**
     * Vista principal del módulo. Separa los eventos por estado y carga
     * asistencias, miembros y bloques del usuario en sesión.
     */
    @GetMapping
    public String calendario(Model model, HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso",               piso);
            model.addAttribute("eventosPropuestos",  calendarioService.obtenerEventosPorEstado(piso.getId(), EstadoEvento.PROPUESTO));
            model.addAttribute("eventosAprobados",   calendarioService.obtenerEventosPorEstado(piso.getId(), EstadoEvento.APROBADO));
            model.addAttribute("eventosRechazados",  calendarioService.obtenerEventosPorEstado(piso.getId(), EstadoEvento.RECHAZADO));
            model.addAttribute("asistencias",        calendarioService.obtenerAsistencias(piso.getId()));
            model.addAttribute("miembros",           calendarioService.obtenerMiembrosActivos(piso.getId()));
            model.addAttribute("misBloques",
                u != null ? calendarioService.obtenerMisBloques(u.getId()) : List.of());
        }
        return "calendario";
    }

    // -----------------------------------------------------------------------
    // Feed JSON para FullCalendar
    // -----------------------------------------------------------------------

    /**
     * Feed de eventos para FullCalendar: combina eventos del piso y bloques personales
     * de todos los miembros en el formato esperado por FullCalendar.
     * Devuelve {@code { ok, data: [ … ] }}.
     */
    /**
     * Feed de eventos para FullCalendar dentro de un rango de fechas.
     * FullCalendar pasa {@code start} y {@code end} como strings ISO (ej: "2025-09-28").
     * Si no se pasan, se usa un rango por defecto de ± 1-3 meses desde hoy.
     */
    @GetMapping(value = "/feed", produces = "application/json")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> feed(
            @RequestParam(required = false, defaultValue = "") String start,
            @RequestParam(required = false, defaultValue = "") String end,
            HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        LocalDate rangeStart = start.length() >= 10 ? LocalDate.parse(start.substring(0, 10))
                                                     : LocalDate.now().minusMonths(1);
        LocalDate rangeEnd   = end.length()   >= 10 ? LocalDate.parse(end.substring(0, 10))
                                                     : LocalDate.now().plusMonths(3);
        return ResponseEntity.ok(ApiResponse.ok(
            calendarioService.obtenerFeed(piso.getId(), u.getId(), rangeStart, rangeEnd)));
    }

    // -----------------------------------------------------------------------
    // Creación de eventos/bloques via AJAX para modales FullCalendar
    // -----------------------------------------------------------------------

    /**
     * Crea un evento desde el modal de FullCalendar.
     * Devuelve {@code { ok: true, data: null }} al crearse correctamente.
     * Las excepciones de negocio son gestionadas por {@link GlobalExceptionHandler}.
     */
    @PostMapping("/evento/crear")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> crearEventoJson(
            @Valid @ModelAttribute EventoForm form, BindingResult errors,
            HttpSession session) {
        if (errors.hasErrors())
            return ResponseEntity.badRequest().body(ApiResponse.error(errors.getAllErrors().get(0).getDefaultMessage()));
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        calendarioService.crearEvento(form, u.getId(), piso);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Crea un bloque horario desde el modal de FullCalendar.
     * Devuelve {@code { ok: true, data: null }} al crearse correctamente.
     */
    @PostMapping("/bloque/crear")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> crearBloqueJson(
            @Valid @ModelAttribute BloqueForm form, BindingResult errors,
            HttpSession session) {
        if (errors.hasErrors())
            return ResponseEntity.badRequest().body(ApiResponse.error(errors.getAllErrors().get(0).getDefaultMessage()));
        User u = (User) session.getAttribute("u");
        if (u == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        calendarioService.crearBloque(form, u);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // -----------------------------------------------------------------------
    // Creación de eventos (MVC — flash messages)
    // -----------------------------------------------------------------------

    /** Crea un evento nuevo en estado PROPUESTO. */
    @PostMapping
    public String crearEvento(@Valid @ModelAttribute EventoForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/modulos/calendario";
        }
        try {
            calendarioService.crearEvento(form, u.getId(), piso);
            ra.addFlashAttribute("flashSuccess", "Evento propuesto correctamente. Espera la aprobación del piso.");
        } catch (IllegalArgumentException e) {
            log.warn("crearEvento: {}", e.getMessage());
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/modulos/calendario";
    }

    // -----------------------------------------------------------------------
    // Aprobación de eventos (AJAX — GlobalExceptionHandler gestiona errores)
    // -----------------------------------------------------------------------

    /**
     * Aprueba un evento: PROPUESTO → APROBADO.
     * Devuelve {@code { ok, data: { estado: "APROBADO" } }}.
     */
    @PostMapping("/evento/{id}/aprobar")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> aprobarEvento(
            @PathVariable long id, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        EstadoEvento estado = calendarioService.aprobarEvento(id, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("estado", estado.name())));
    }

    /**
     * Rechaza un evento: PROPUESTO/APROBADO → RECHAZADO.
     * Devuelve {@code { ok, data: { estado: "RECHAZADO" } }}.
     */
    @PostMapping("/evento/{id}/rechazar")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> rechazarEvento(
            @PathVariable long id, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        EstadoEvento estado = calendarioService.rechazarEvento(id, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("estado", estado.name())));
    }

    /**
     * Reabre un evento rechazado: RECHAZADO → PROPUESTO.
     * Devuelve {@code { ok, data: { estado: "PROPUESTO" } }}.
     */
    @PostMapping("/evento/{id}/reabrir")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> reabrirEvento(
            @PathVariable long id, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        EstadoEvento estado = calendarioService.reabrirEvento(id, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("estado", estado.name())));
    }

    // -----------------------------------------------------------------------
    // Asistencia individual — solo en eventos APROBADO (AJAX)
    // -----------------------------------------------------------------------

    /**
     * Alterna la asistencia del usuario al evento de forma cíclica.
     * Solo funciona en eventos APROBADO.
     * Devuelve {@code { ok, data: { estado } }}.
     */
    @PostMapping("/asistencia/{eventoId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> toggleAsistencia(
            @PathVariable long eventoId, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        EstadoAsistencia estado = calendarioService.toggleAsistencia(eventoId, u.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("estado", estado.name())));
    }

    // -----------------------------------------------------------------------
    // Bloques horarios personales
    // -----------------------------------------------------------------------

    /** Crea un bloque horario personal para el usuario en sesión. */
    @PostMapping("/bloque")
    public String crearBloque(@Valid @ModelAttribute BloqueForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/modulos/calendario";
        }
        try {
            calendarioService.crearBloque(form, u);
            ra.addFlashAttribute("flashSuccess", "Bloque horario añadido.");
        } catch (IllegalArgumentException e) {
            log.warn("crearBloque: {}", e.getMessage());
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/modulos/calendario";
    }

    /**
     * Elimina un bloque horario del usuario en sesión.
     * Solo el propietario puede eliminarlo.
     * Devuelve {@code { ok, data: null }}.
     */
    @DeleteMapping("/bloque/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> eliminarBloque(
            @PathVariable long id, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        calendarioService.eliminarBloque(id, u.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // -----------------------------------------------------------------------
    // Detección de conflictos (AJAX)
    // -----------------------------------------------------------------------

    /**
     * Detecta conflictos entre un evento y los bloques horarios de los miembros.
     * Disponible para cualquier estado de evento (útil antes de aprobar).
     * Devuelve {@code { ok, data: [ {usuario, tipo, descripcion, inicio, fin}, ... ] }}.
     */
    @GetMapping("/conflictos/{eventoId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> conflictos(
            @PathVariable long eventoId, HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        List<Map<String, Object>> lista = calendarioService.detectarConflictos(eventoId, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }
}
