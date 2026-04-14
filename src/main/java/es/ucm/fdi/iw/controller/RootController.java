package es.ucm.fdi.iw.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * <p>
 * Gestiona las rutas públicas (login, índice, autores) y todos los módulos
 * funcionales del piso: home, gastos, lista de compra, tareas y calendario.
 * El piso del usuario se resuelve en cada petición a partir de la sesión,
 * de modo que no se almacena estado entre peticiones.
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

    /**
     * Devuelve el piso al que pertenece el usuario en sesión,
     * o {@code null} si no hay sesión activa o el usuario no está en ningún piso.
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

    // --- MÓDULO HOME ---

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

    // --- MÓDULO GASTOS ---

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

    /**
     * Registra un nuevo gasto y reparte su importe a partes iguales entre todos
     * los miembros del piso. El pagador queda marcado como {@code pagado = true}
     * en su registro de participante. Se hace {@code flush} antes de crear los
     * participantes para garantizar que el gasto ya tiene id asignado.
     */
    @PostMapping("/modulos/gastos")
    @Transactional
    public String crearGasto(@RequestParam String concepto,
            @RequestParam BigDecimal importe,
            HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = resolverPiso(session);
        if (u == null || piso == null)
            return "redirect:/login";

        // Validación básica del servidor
        if (concepto == null || concepto.isBlank())
            return "redirect:/modulos/gastos";
        if (importe == null || importe.compareTo(BigDecimal.ZERO) <= 0)
            return "redirect:/modulos/gastos";

        Gasto g = new Gasto();
        g.setConcepto(concepto.trim());
        g.setImporte(importe);
        g.setFecha(LocalDate.now());
        g.setPagador(entityManager.find(User.class, u.getId()));
        g.setPiso(piso);
        g.setEstado(EstadoGasto.PENDIENTE);
        entityManager.persist(g);
        entityManager.flush(); // necesario para que g tenga id antes de crear participantes

        // Repartir el importe entre los miembros del piso a partes iguales
        List<MiembroPiso> miembros = entityManager
                .createQuery("SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid", MiembroPiso.class)
                .setParameter("pid", piso.getId())
                .getResultList();

        if (!miembros.isEmpty()) {
            BigDecimal cuota = importe.divide(BigDecimal.valueOf(miembros.size()), 2, RoundingMode.HALF_UP);
            for (MiembroPiso mp : miembros) {
                ParticipanteGasto pg = new ParticipanteGasto();
                pg.setGasto(g);
                pg.setUsuario(mp.getUsuario());
                pg.setImporteAsignado(cuota);
                // El pagador ya ha desembolsado su parte
                pg.setPagado(mp.getUsuario().getId() == u.getId());
                entityManager.persist(pg);
            }
        }

        log.info("Gasto '{}' creado por {} — {} €, repartido entre {} miembros",
                concepto.trim(), u.getUsername(), importe, miembros.size());
        return "redirect:/modulos/gastos";
    }

    /**
     * Marca la participación del usuario en un gasto como pagada.
     * Solo el propio usuario puede marcar su parte. Si tras el pago
     * todos los participantes del gasto están al corriente, el gasto
     * pasa a estado LIQUIDADO.
     */
    @PostMapping("/modulos/gastos/participante/{id}/pagar")
    @Transactional
    @ResponseBody
    public Map<String, Object> pagarParticipacion(@PathVariable long id, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("error", "no autorizado");

        ParticipanteGasto pg = entityManager.find(ParticipanteGasto.class, id);
        if (pg == null)
            return Map.of("error", "participación no encontrada");

        if (pg.getUsuario().getId() != u.getId())
            return Map.of("error", "no autorizado");

        if (pg.isPagado())
            return Map.of("error", "ya estaba pagado");

        pg.setPagado(true);

        long pendientes = entityManager
                .createQuery(
                        "SELECT COUNT(p) FROM ParticipanteGasto p WHERE p.gasto.id = :gid AND p.pagado = false",
                        Long.class)
                .setParameter("gid", pg.getGasto().getId())
                .getSingleResult();

        boolean liquidado = pendientes == 0;
        if (liquidado)
            pg.getGasto().setEstado(EstadoGasto.LIQUIDADO);

        log.info("Participante {} marcó su parte del gasto '{}' como pagada{}",
                u.getUsername(), pg.getGasto().getConcepto(), liquidado ? " — gasto liquidado" : "");

        return Map.of("pagado", true, "liquidado", liquidado);
    }

    // --- MÓDULO COMPRA ---

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

    @GetMapping("/modulos/compra/gestion")
    public String gestionCompra(Model model, HttpSession session) {
        Piso piso = resolverPiso(session);
        if (piso == null)
            return "redirect:/login";

        model.addAttribute("piso", piso);

        List<ListaCompra> listas = entityManager
                .createQuery("SELECT lc FROM ListaCompra lc WHERE lc.piso.id = :pid ORDER BY lc.fechaCreacion DESC",
                        ListaCompra.class)
                .setParameter("pid", piso.getId())
                .getResultList();
        model.addAttribute("listas", listas);

        List<Producto> productos = entityManager
                .createQuery("SELECT p FROM Producto p WHERE p.piso.id = :pid ORDER BY p.categoria, p.nombre",
                        Producto.class)
                .setParameter("pid", piso.getId())
                .getResultList();
        model.addAttribute("productos", productos);

        return "compra-gestion";
    }

    /** Solo el administrador del piso puede crear nuevas listas de la compra. */
    @PostMapping("/modulos/compra/lista")
    @Transactional
    public String crearLista(@RequestParam String nombre, HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = resolverPiso(session);
        if (u == null || piso == null)
            return "redirect:/login";
        if (!u.hasRole(User.Role.ADMIN))
            return "redirect:/modulos/compra/gestion";
        if (nombre == null || nombre.isBlank())
            return "redirect:/modulos/compra/gestion";

        ListaCompra lista = new ListaCompra();
        lista.setNombre(nombre.trim());
        lista.setFechaCreacion(LocalDate.now());
        lista.setCompletada(false);
        lista.setPiso(piso);
        entityManager.persist(lista);

        log.info("Lista '{}' creada por {}", nombre.trim(), u.getUsername());
        return "redirect:/modulos/compra/gestion";
    }

    @PostMapping("/modulos/compra/producto")
    @Transactional
    public String crearProducto(@RequestParam String nombre,
            @RequestParam(required = false) String categoria,
            HttpSession session) {
        User u = (User) session.getAttribute("u");
        Piso piso = resolverPiso(session);
        if (u == null || piso == null)
            return "redirect:/login";
        if (nombre == null || nombre.isBlank())
            return "redirect:/modulos/compra/gestion";

        Producto p = new Producto();
        p.setNombre(nombre.trim());
        p.setCategoria(categoria != null && !categoria.isBlank() ? categoria.trim() : "General");
        p.setPiso(piso);
        entityManager.persist(p);

        log.info("Producto '{}' añadido al catálogo por {}", nombre.trim(), u.getUsername());
        return "redirect:/modulos/compra/gestion";
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

    // --- MÓDULO TAREAS ---

    @GetMapping("/modulos/tareas")
    public String tareas(Model model, HttpSession session) {
        Piso piso = resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso", piso);

            List<AsignacionTarea> pendientes = entityManager
                    .createQuery(
                            "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid"
                            + " AND at.fechaCompletada IS NULL ORDER BY at.tarea.fechaLimite ASC",
                            AsignacionTarea.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("pendientes", pendientes);

            List<AsignacionTarea> realizadas = entityManager
                    .createQuery(
                            "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid"
                            + " AND at.fechaCompletada IS NOT NULL ORDER BY at.fechaCompletada DESC",
                            AsignacionTarea.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("realizadas", realizadas);

            // Tareas creadas pero sin asignar a ningún miembro todavía
            List<Tarea> sinAsignar = entityManager
                    .createQuery(
                            "SELECT t FROM Tarea t WHERE t.piso.id = :pid"
                            + " AND NOT EXISTS (SELECT at FROM AsignacionTarea at WHERE at.tarea.id = t.id)"
                            + " ORDER BY t.fechaLimite ASC",
                            Tarea.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("sinAsignar", sinAsignar);

            List<MiembroPiso> miembros = entityManager
                    .createQuery("SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid", MiembroPiso.class)
                    .setParameter("pid", piso.getId())
                    .getResultList();
            model.addAttribute("miembros", miembros);
        }
        return "tareas";
    }

    /**
     * Crea una nueva tarea en el piso y, opcionalmente, genera una asignación
     * si se ha seleccionado un miembro responsable.
     */
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

        if (nombre == null || nombre.isBlank() || fechaLimite == null || fechaLimite.isBlank())
            return "redirect:/modulos/tareas";

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

    // --- MÓDULO CALENDARIO ---

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

        if (titulo == null || titulo.isBlank())
            return "redirect:/modulos/calendario";
        LocalDateTime inicio = LocalDateTime.parse(fechaInicio);
        LocalDateTime fin = LocalDateTime.parse(fechaFin);
        if (!fin.isAfter(inicio))
            return "redirect:/modulos/calendario";

        Evento e = new Evento();
        e.setTitulo(titulo.trim());
        e.setDescripcion(descripcion);
        e.setFechaInicio(inicio);
        e.setFechaFin(fin);
        e.setCreador(entityManager.find(User.class, u.getId()));
        e.setPiso(piso);
        entityManager.persist(e);

        log.info("Evento creado: '{}' en piso {}", titulo.trim(), piso.getNombre());
        return "redirect:/modulos/calendario";
    }

    /**
     * Alterna la asistencia del usuario a un evento de forma cíclica:
     * si no existe registro previo se crea como CONFIRMADO; si ya existe,
     * rota CONFIRMADO → RECHAZADO → PENDIENTE → CONFIRMADO.
     * Devuelve el nuevo estado como JSON para que el cliente lo refleje
     * sin recargar la página.
     *
     * @param eventoId identificador del evento
     */
    @PostMapping("/modulos/calendario/asistencia/{eventoId}")
    @Transactional
    @ResponseBody
    public Map<String, Object> toggleAsistencia(@PathVariable long eventoId, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return Map.of("error", "no autorizado");

        List<AsistenciaEvento> existing = entityManager
                .createQuery(
                        "SELECT ae FROM AsistenciaEvento ae WHERE ae.evento.id = :eid AND ae.usuario.id = :uid",
                        AsistenciaEvento.class)
                .setParameter("eid", eventoId)
                .setParameter("uid", u.getId())
                .getResultList();

        EstadoAsistencia nuevoEstado;
        if (existing.isEmpty()) {
            Evento evento = entityManager.find(Evento.class, eventoId);
            if (evento == null)
                return Map.of("error", "evento no encontrado");
            AsistenciaEvento ae = new AsistenciaEvento();
            ae.setEvento(evento);
            ae.setUsuario(entityManager.find(User.class, u.getId()));
            ae.setEstado(EstadoAsistencia.CONFIRMADO);
            entityManager.persist(ae);
            nuevoEstado = EstadoAsistencia.CONFIRMADO;
        } else {
            AsistenciaEvento ae = existing.get(0);
            nuevoEstado = switch (ae.getEstado()) {
                case PENDIENTE -> EstadoAsistencia.CONFIRMADO;
                case CONFIRMADO -> EstadoAsistencia.RECHAZADO;
                case RECHAZADO -> EstadoAsistencia.PENDIENTE;
            };
            ae.setEstado(nuevoEstado);
        }

        log.info("Asistencia de {} al evento {} -> {}", u.getUsername(), eventoId, nuevoEstado);
        return Map.of("estado", nuevoEstado.name());
    }
}
