package es.ucm.fdi.iw.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import es.ucm.fdi.iw.model.*;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * Controlador principal de SyncFlat.
 * Gestiona las rutas públicas y los módulos funcionales del piso.
 */
@Controller
public class RootController {

    private static final Logger log = LogManager.getLogger(RootController.class);

    @Autowired
    private EntityManager entityManager;

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    // ======== Utilidad: resolver piso del usuario logueado ========

    /**
     * Busca el piso al que pertenece el usuario logueado.
     * Devuelve null si no hay usuario en sesión o no pertenece a ningún piso.
     */
    private Piso resolverPiso(HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return null;
        List<MiembroPiso> memberships = entityManager
                .createQuery("SELECT mp FROM MiembroPiso mp WHERE mp.usuario.id = :uid", MiembroPiso.class)
                .setParameter("uid", u.getId())
                .getResultList();
        return memberships.isEmpty() ? null : memberships.get(0).getPiso();
    }

    // ======== Páginas públicas ========

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        boolean error = request.getQueryString() != null && request.getQueryString().indexOf("error") != -1;
        model.addAttribute("loginError", error);
        return "login";
    }

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @GetMapping("/autores")
    public String autores(Model model) {
        return "autores";
    }

    // ======== MÓDULO HOME: Dashboard del piso ========

    @GetMapping("/modulos/home")
    public String home(Model model, HttpSession session) {
        Piso piso = resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso", piso);

            // Próximos eventos (los 5 más cercanos en el futuro)
            List<Evento> eventos = entityManager
                    .createQuery(
                            "SELECT e FROM Evento e WHERE e.piso.id = :pid AND e.fechaInicio >= :now ORDER BY e.fechaInicio",
                            Evento.class)
                    .setParameter("pid", piso.getId())
                    .setParameter("now", LocalDateTime.now())
                    .setMaxResults(5)
                    .getResultList();
            model.addAttribute("eventos", eventos);

            // Tareas pendientes (asignaciones sin completar)
            List<AsignacionTarea> tareasPendientes = entityManager
                    .createQuery(
                            "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid AND at.fechaCompletada IS NULL ORDER BY at.tarea.fechaLimite",
                            AsignacionTarea.class)
                    .setParameter("pid", piso.getId())
                    .setMaxResults(5)
                    .getResultList();
            model.addAttribute("tareasPendientes", tareasPendientes);

            // Gastos recientes (últimos 5)
            List<Gasto> gastos = entityManager
                    .createQuery("SELECT g FROM Gasto g WHERE g.piso.id = :pid ORDER BY g.fecha DESC", Gasto.class)
                    .setParameter("pid", piso.getId())
                    .setMaxResults(5)
                    .getResultList();
            model.addAttribute("gastos", gastos);

            // Alertas no leídas
            List<Alerta> alertas = entityManager
                    .createQuery(
                            "SELECT a FROM Alerta a WHERE a.piso.id = :pid AND a.leida = false ORDER BY a.fecha DESC",
                            Alerta.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("alertas", alertas);
        }
        return "home";
    }

    // ======== MÓDULO GASTOS ========

    @GetMapping("/modulos/gastos")
    public String gastos(Model model, HttpSession session) {
        Piso piso = resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso", piso);

            List<Gasto> gastos = entityManager
                    .createQuery("SELECT g FROM Gasto g WHERE g.piso.id = :pid ORDER BY g.fecha DESC", Gasto.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("gastos", gastos);

            // Participantes de todos los gastos del piso
            List<ParticipanteGasto> participantes = entityManager
                    .createQuery("SELECT pg FROM ParticipanteGasto pg WHERE pg.gasto.piso.id = :pid",
                            ParticipanteGasto.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("participantes", participantes);

            // Miembros del piso (para el formulario)
            List<MiembroPiso> miembros = entityManager
                    .createQuery("SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid", MiembroPiso.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("miembros", miembros);
        }
        return "gastos";
    }

    @PostMapping("/modulos/gastos")
    @Transactional
    public String crearGasto(@RequestParam String concepto,
            @RequestParam BigDecimal importe,
            HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = resolverPiso(session);
        if (u == null || piso == null)
            return "redirect:/login";

        Gasto g = new Gasto();
        g.setConcepto(concepto);
        g.setImporte(importe);
        g.setFecha(LocalDate.now());
        g.setPagador(entityManager.find(User.class, u.getId()));
        g.setPiso(piso);
        g.setEstado(EstadoGasto.PENDIENTE);
        entityManager.persist(g);

        log.info("Gasto creado: '{}' por {} — {} €", concepto, u.getUsername(), importe);
        return "redirect:/modulos/gastos";
    }

    // ======== MÓDULO COMPRA ========

    @GetMapping("/modulos/compra")
    public String compra(Model model, HttpSession session) {
        Piso piso = resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso", piso);

            List<ListaCompra> listas = entityManager
                    .createQuery("SELECT lc FROM ListaCompra lc WHERE lc.piso.id = :pid ORDER BY lc.fechaCreacion DESC",
                            ListaCompra.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("listas", listas);

            // Items de todas las listas del piso
            List<ItemListaCompra> items = entityManager
                    .createQuery(
                            "SELECT i FROM ItemListaCompra i WHERE i.lista.piso.id = :pid ORDER BY i.comprado ASC, i.id DESC",
                            ItemListaCompra.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("items", items);

            // Productos del piso (para el formulario)
            List<Producto> productos = entityManager
                    .createQuery("SELECT p FROM Producto p WHERE p.piso.id = :pid ORDER BY p.nombre", Producto.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("productos", productos);
        }
        return "compra";
    }

    @PostMapping("/modulos/compra/item")
    @Transactional
    public String crearItem(@RequestParam long listaId,
            @RequestParam long productoId,
            @RequestParam int cantidad,
            HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return "redirect:/login";

        ListaCompra lista = entityManager.find(ListaCompra.class, listaId);
        Producto producto = entityManager.find(Producto.class, productoId);
        if (lista == null || producto == null)
            return "redirect:/modulos/compra";

        ItemListaCompra item = new ItemListaCompra();
        item.setLista(lista);
        item.setProducto(producto);
        item.setCantidad(cantidad);
        item.setComprado(false);
        item.setSolicitadoPor(entityManager.find(User.class, u.getId()));
        entityManager.persist(item);

        log.info("Item añadido a lista '{}': {} x{}", lista.getNombre(), producto.getNombre(), cantidad);
        return "redirect:/modulos/compra";
    }

    @PostMapping("/modulos/compra/item/{id}/toggle")
    @Transactional
    @ResponseBody
    public Map<String, Object> toggleItem(@PathVariable long id, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("error", "no autorizado");

        ItemListaCompra item = entityManager.find(ItemListaCompra.class, id);
        if (item == null)
            return Map.of("error", "item no encontrado");

        item.setComprado(!item.isComprado());
        log.info("Item {} marcado como {}", id, item.isComprado() ? "comprado" : "pendiente");
        return Map.of("comprado", item.isComprado());
    }

    // ======== MÓDULO TAREAS ========

    @GetMapping("/modulos/tareas")
    public String tareas(Model model, HttpSession session) {
        Piso piso = resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso", piso);

            List<Tarea> tareas = entityManager
                    .createQuery("SELECT t FROM Tarea t WHERE t.piso.id = :pid ORDER BY t.fechaLimite", Tarea.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("tareas", tareas);

            List<AsignacionTarea> asignaciones = entityManager
                    .createQuery(
                            "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid ORDER BY at.fechaAsignacion DESC",
                            AsignacionTarea.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("asignaciones", asignaciones);

            List<MiembroPiso> miembros = entityManager
                    .createQuery("SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid", MiembroPiso.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("miembros", miembros);
        }
        return "tareas";
    }

    @PostMapping("/modulos/tareas")
    @Transactional
    public String crearTarea(@RequestParam String nombre,
            @RequestParam String descripcion,
            @RequestParam String tipo,
            @RequestParam(required = false) String frecuencia,
            @RequestParam String fechaLimite,
            @RequestParam(required = false) Long asignadoId,
            HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = resolverPiso(session);
        if (u == null || piso == null)
            return "redirect:/login";

        Tarea t = new Tarea();
        t.setNombre(nombre);
        t.setDescripcion(descripcion);
        t.setTipo(TipoTarea.valueOf(tipo));
        t.setFrecuencia(frecuencia);
        t.setFechaLimite(LocalDate.parse(fechaLimite));
        t.setPiso(piso);
        entityManager.persist(t);
        entityManager.flush();

        // Si se ha asignado a alguien, crear la asignación
        if (asignadoId != null) {
            AsignacionTarea at = new AsignacionTarea();
            at.setTarea(t);
            at.setUsuario(entityManager.find(User.class, asignadoId));
            at.setFechaAsignacion(LocalDate.now());
            at.setValidada(false);
            entityManager.persist(at);
        }

        log.info("Tarea creada: '{}' en piso {}", nombre, piso.getNombre());
        return "redirect:/modulos/tareas";
    }

    @PostMapping("/modulos/tareas/{id}/completar")
    @Transactional
    @ResponseBody
    public Map<String, Object> completarTarea(@PathVariable long id, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("error", "no autorizado");

        AsignacionTarea at = entityManager.find(AsignacionTarea.class, id);
        if (at == null)
            return Map.of("error", "asignación no encontrada");

        if (at.getFechaCompletada() == null) {
            at.setFechaCompletada(LocalDate.now());
            log.info("Tarea '{}' completada por {}", at.getTarea().getNombre(), u.getUsername());
        } else {
            at.setFechaCompletada(null);
            log.info("Tarea '{}' reabierta", at.getTarea().getNombre());
        }
        return Map.of("completada", at.getFechaCompletada() != null);
    }

    // ======== MÓDULO CALENDARIO ========

    @GetMapping("/modulos/calendario")
    public String calendario(Model model, HttpSession session) {
        Piso piso = resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso", piso);

            List<Evento> eventos = entityManager
                    .createQuery("SELECT e FROM Evento e WHERE e.piso.id = :pid ORDER BY e.fechaInicio DESC",
                            Evento.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("eventos", eventos);

            List<AsistenciaEvento> asistencias = entityManager
                    .createQuery("SELECT ae FROM AsistenciaEvento ae WHERE ae.evento.piso.id = :pid",
                            AsistenciaEvento.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("asistencias", asistencias);

            List<MiembroPiso> miembros = entityManager
                    .createQuery("SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid", MiembroPiso.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("miembros", miembros);
        }
        return "calendario";
    }

    @PostMapping("/modulos/calendario")
    @Transactional
    public String crearEvento(@RequestParam String titulo,
            @RequestParam String descripcion,
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin,
            HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = resolverPiso(session);
        if (u == null || piso == null)
            return "redirect:/login";

        Evento e = new Evento();
        e.setTitulo(titulo);
        e.setDescripcion(descripcion);
        e.setFechaInicio(LocalDateTime.parse(fechaInicio));
        e.setFechaFin(LocalDateTime.parse(fechaFin));
        e.setCreador(entityManager.find(User.class, u.getId()));
        e.setPiso(piso);
        entityManager.persist(e);

        log.info("Evento creado: '{}' en piso {}", titulo, piso.getNombre());
        return "redirect:/modulos/calendario";
    }
}
