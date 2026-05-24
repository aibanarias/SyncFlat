package es.ucm.fdi.iw.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.ucm.fdi.iw.dto.CompraDetalle;
import es.ucm.fdi.iw.dto.ItemForm;
import es.ucm.fdi.iw.dto.ListaForm;
import es.ucm.fdi.iw.dto.ProductoForm;
import es.ucm.fdi.iw.dto.RegistrarCompraForm;
import es.ucm.fdi.iw.model.Compra;
import es.ucm.fdi.iw.model.EstadoGasto;
import es.ucm.fdi.iw.model.Gasto;
import es.ucm.fdi.iw.model.ItemListaCompra;
import es.ucm.fdi.iw.model.ListaCompra;
import es.ucm.fdi.iw.model.MiembroPiso;
import es.ucm.fdi.iw.model.ParticipanteGasto;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.Producto;
import es.ucm.fdi.iw.model.TipoAlerta;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;

/**
 * Lógica de negocio del módulo de Compra.
 * <p>
 * Gestiona el catálogo de productos del piso, las listas de la compra,
 * los ítems individuales y el registro de compras realizadas.
 * <p>
 * El ciclo completo de una lista es:
 * <ol>
 *   <li><b>ACTIVA</b>: {@code completada = false} — los miembros añaden ítems y los van marcando.</li>
 *   <li><b>REGISTRADA</b>: {@code completada = true} — se ha ejecutado {@link #registrarCompra}
 *       generando un {@link Compra} y, opcionalmente, un {@link Gasto} compartido.</li>
 * </ol>
 */
@RequiredArgsConstructor
@Service
public class CompraService {

    private static final Logger log = LogManager.getLogger(CompraService.class);

    private final EntityManager entityManager;
    private final AlertaService alertaService;

    // -------------------------------------------------------------------------
    // Consultas de solo lectura
    // -------------------------------------------------------------------------

    /** Devuelve todas las listas del piso, ordenadas por fecha descendente. */
    @Transactional(readOnly = true)
    public List<ListaCompra> obtenerListas(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT lc FROM ListaCompra lc WHERE lc.piso.id = :pid ORDER BY lc.fechaCreacion DESC",
                ListaCompra.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve las listas activas (no completadas) del piso, por fecha descendente. */
    @Transactional(readOnly = true)
    public List<ListaCompra> obtenerListasActivas(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT lc FROM ListaCompra lc WHERE lc.piso.id = :pid AND lc.completada = false"
                + " ORDER BY lc.fechaCreacion DESC",
                ListaCompra.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve las listas ya completadas del piso, por fecha descendente. */
    @Transactional(readOnly = true)
    public List<ListaCompra> obtenerListasCompletadas(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT lc FROM ListaCompra lc WHERE lc.piso.id = :pid AND lc.completada = true"
                + " ORDER BY lc.fechaCreacion DESC",
                ListaCompra.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /**
     * Devuelve los ítems de las listas activas del piso.
     * Los pendientes aparecen primero; los comprados al final.
     */
    @Transactional(readOnly = true)
    public List<ItemListaCompra> obtenerItems(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT i FROM ItemListaCompra i WHERE i.lista.piso.id = :pid"
                + " AND i.lista.completada = false ORDER BY i.comprado ASC, i.id DESC",
                ItemListaCompra.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve el catálogo de productos del piso ordenado por nombre. */
    @Transactional(readOnly = true)
    public List<Producto> obtenerProductos(long pisoId) {
        return entityManager
            .createQuery("SELECT p FROM Producto p WHERE p.piso.id = :pid ORDER BY p.nombre", Producto.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve el catálogo de productos ordenado por categoría y nombre (para la vista de gestión). */
    @Transactional(readOnly = true)
    public List<Producto> obtenerProductosOrdenados(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT p FROM Producto p WHERE p.piso.id = :pid ORDER BY p.categoria, p.nombre",
                Producto.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve las compras registradas en el piso, por fecha descendente. */
    @Transactional(readOnly = true)
    public List<Compra> obtenerCompras(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT c FROM Compra c WHERE c.piso.id = :pid ORDER BY c.fecha DESC",
                Compra.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /**
     * Historial de compras enriquecido: cada compra viene con los ítems que se marcaron
     * como comprados en su lista (los no marcados quedan fuera).
     */
    @Transactional(readOnly = true)
    public List<CompraDetalle> obtenerHistorialCompras(long pisoId) {
        List<Compra> compras = entityManager
            .createQuery("SELECT c FROM Compra c WHERE c.piso.id = :pid ORDER BY c.fecha DESC", Compra.class)
            .setParameter("pid", pisoId)
            .getResultList();
        if (compras.isEmpty()) return List.of();

        List<Long> listaIds = compras.stream().map(c -> c.getLista().getId()).toList();
        List<ItemListaCompra> comprados = entityManager
            .createQuery("SELECT i FROM ItemListaCompra i WHERE i.lista.id IN :ids AND i.comprado = true",
                ItemListaCompra.class)
            .setParameter("ids", listaIds)
            .getResultList();

        Map<Long, List<ItemListaCompra>> porLista = new HashMap<>();
        for (ItemListaCompra i : comprados) {
            porLista.computeIfAbsent(i.getLista().getId(), k -> new ArrayList<>()).add(i);
        }
        return compras.stream()
            .map(c -> new CompraDetalle(c, porLista.getOrDefault(c.getLista().getId(), List.of())))
            .toList();
    }

    /** Devuelve los miembros activos del piso para el formulario de crear lista. */
    @Transactional(readOnly = true)
    public List<MiembroPiso> obtenerMiembrosActivos(long pisoId) {
        return cargarMiembrosActivos(pisoId);
    }

    // -------------------------------------------------------------------------
    // Operaciones de escritura
    // -------------------------------------------------------------------------

    /**
     * Crea una nueva lista de la compra con los participantes elegidos.
     * Si no se elige ningún participante, la lista queda abierta a todos los miembros
     * (el reparto económico usa fallback al cerrar la compra).
     *
     * @param form datos validados del formulario (incluye participanteIds)
     * @param piso piso al que pertenece la lista
     */
    @Transactional
    public void crearLista(ListaForm form, Piso piso) {
        ListaCompra lista = new ListaCompra();
        lista.setNombre(form.getNombre().trim());
        lista.setFechaCreacion(LocalDate.now());
        lista.setCompletada(false);
        lista.setPiso(piso);

        List<Long> ids = form.getParticipanteIds();
        if (ids != null && !ids.isEmpty()) {
            for (Long uid : ids) {
                User u = entityManager.find(User.class, uid);
                if (u != null) lista.getParticipantes().add(u);
            }
        }
        entityManager.persist(lista);
        log.info("Lista '{}' creada en piso {} con {} participante(s)",
            lista.getNombre(), piso.getNombre(),
            lista.getParticipantes().isEmpty() ? "todos los" : lista.getParticipantes().size());
    }

    /**
     * Añade un producto al catálogo del piso.
     *
     * @param form datos validados del formulario
     * @param piso piso al que pertenece el producto
     */
    @Transactional
    public void crearProducto(ProductoForm form, Piso piso) {
        Producto p = new Producto();
        p.setNombre(form.getNombre().trim());
        p.setCategoria(form.getCategoria() != null && !form.getCategoria().isBlank()
            ? form.getCategoria().trim() : "General");
        p.setPiso(piso);
        entityManager.persist(p);
        log.info("Producto '{}' añadido al catálogo de piso {}", p.getNombre(), piso.getNombre());
    }

    /**
     * Añade un ítem a una lista de la compra activa.
     *
     * @param form           datos validados del formulario
     * @param solicitadoPorId usuario que solicita el ítem
     * @throws IllegalArgumentException si la lista o el producto no existen
     * @throws IllegalStateException    si la lista ya está completada
     */
    @Transactional
    public void crearItem(ItemForm form, long solicitadoPorId, long pisoId) {
        ListaCompra lista = entityManager.find(ListaCompra.class, form.getListaId());
        Producto producto = entityManager.find(Producto.class, form.getProductoId());
        if (lista == null || producto == null)
            throw new IllegalArgumentException("lista o producto no encontrado");
        if (lista.getPiso().getId() != pisoId || producto.getPiso().getId() != pisoId)
            throw new SecurityException("La lista o el producto no pertenecen a tu piso.");
        if (lista.isCompletada())
            throw new IllegalStateException("No se pueden añadir ítems a una lista ya registrada.");

        ItemListaCompra item = new ItemListaCompra();
        item.setLista(lista);
        item.setProducto(producto);
        item.setCantidad(form.getCantidad());
        item.setComprado(false);
        item.setSolicitadoPor(entityManager.find(User.class, solicitadoPorId));
        entityManager.persist(item);
        log.info("Item añadido a '{}': {} x{}", lista.getNombre(), producto.getNombre(), form.getCantidad());
    }

    /**
     * Alterna el estado comprado/pendiente de un ítem.
     * <p>
     * Falla si la lista ya ha sido cerrada para preservar la integridad del historial:
     * los ítems marcados al cierre son los que se consideraron comprados.
     *
     * @param itemId identificador del ítem
     * @return nuevo valor de {@code comprado}
     * @throws IllegalArgumentException si el ítem no existe
     * @throws IllegalStateException    si la lista del ítem ya está cerrada
     */
    @Transactional
    public boolean toggleItem(long itemId, long pisoId) {
        ItemListaCompra item = entityManager.find(ItemListaCompra.class, itemId);
        if (item == null) throw new IllegalArgumentException("item no encontrado");
        if (item.getLista().getPiso().getId() != pisoId)
            throw new SecurityException("El ítem no pertenece a tu piso.");
        if (item.getLista().isCompletada())
            throw new IllegalStateException("No se puede modificar un ítem de una lista ya cerrada.");
        item.setComprado(!item.isComprado());
        log.info("Item {} -> {}", itemId, item.isComprado() ? "comprado" : "pendiente");
        return item.isComprado();
    }

    /**
     * Registra una compra física cerrando la lista asociada.
     *
     * <p>La lista queda marcada como {@code completada = true}. Si
     * {@code form.crearGasto} es {@code true} se genera además un {@link Gasto}
     * compartido repartido a partes iguales entre los miembros activos del piso.
     *
     * @param form      datos validados del formulario
     * @param comprador usuario que realizó la compra
     * @param piso      piso al que pertenece la lista
     * @return la {@link Compra} persistida
     * @throws IllegalArgumentException si la lista no existe o no pertenece al piso
     * @throws IllegalStateException    si la lista ya estaba completada
     */
    @Transactional
    public Compra registrarCompra(RegistrarCompraForm form, User comprador, Piso piso) {
        ListaCompra lista = entityManager.find(ListaCompra.class, form.getListaId());
        if (lista == null)
            throw new IllegalArgumentException("Lista no encontrada.");
        if (lista.getPiso().getId() != piso.getId())
            throw new IllegalArgumentException("La lista no pertenece a tu piso.");
        if (lista.isCompletada())
            throw new IllegalStateException("Esta lista ya fue registrada como compra.");

        Compra compra = new Compra();
        compra.setFecha(form.getFecha() != null && !form.getFecha().isBlank()
            ? LocalDate.parse(form.getFecha()) : LocalDate.now());
        compra.setImporteTotal(form.getImporteTotal());
        compra.setComprador(entityManager.find(User.class, comprador.getId()));
        compra.setLista(lista);
        compra.setPiso(piso);

        if (form.isCrearGasto()) {
            Gasto g = crearGastoDesdeCompra(lista, form.getImporteTotal(), comprador, piso);
            compra.setGasto(g);
        }

        entityManager.persist(compra);
        lista.setCompletada(true);

        alertaService.crearAlerta(
            "Compra realizada: " + lista.getNombre() + " (" + form.getImporteTotal() + " €)",
            TipoAlerta.INFO, piso);
        log.info("Compra registrada: lista '{}', comprador {}, {} €{}",
            lista.getNombre(), comprador.getUsername(), form.getImporteTotal(),
            form.isCrearGasto() ? " + gasto creado" : "");
        return compra;
    }

    /**
     * Crea un {@link Gasto} compartido repartido entre los participantes de la lista.
     * Si la lista no tiene participantes explícitos, reparte entre todos los miembros activos del piso.
     * El comprador queda marcado como {@code pagado = true} desde el inicio.
     */
    private Gasto crearGastoDesdeCompra(ListaCompra lista, BigDecimal importe,
            User comprador, Piso piso) {
        User compradorManaged = entityManager.find(User.class, comprador.getId());

        Gasto g = new Gasto();
        g.setConcepto("Compra: " + lista.getNombre());
        g.setImporte(importe);
        g.setFecha(LocalDate.now());
        g.setPagador(compradorManaged);
        g.setPiso(piso);
        g.setEstado(EstadoGasto.PENDIENTE);
        entityManager.persist(g);
        entityManager.flush();

        // Participantes de la lista; si no hay, fallback a todos los miembros activos
        List<User> participantes = lista.getParticipantes();
        if (participantes.isEmpty()) {
            participantes = cargarMiembrosActivos(piso.getId()).stream()
                .map(MiembroPiso::getUsuario).toList();
        }

        if (!participantes.isEmpty()) {
            BigDecimal cuota = importe.divide(BigDecimal.valueOf(participantes.size()), 2, RoundingMode.HALF_UP);
            for (User u : participantes) {
                ParticipanteGasto pg = new ParticipanteGasto();
                pg.setGasto(g);
                pg.setUsuario(u);
                pg.setImporteAsignado(cuota);
                pg.setPagado(u.getId() == comprador.getId());
                entityManager.persist(pg);
            }
        }
        log.info("Gasto 'Compra: {}' creado — {} € repartido entre {} participante(s)",
            lista.getNombre(), importe, participantes.size());
        return g;
    }

    private List<MiembroPiso> cargarMiembrosActivos(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }
}
