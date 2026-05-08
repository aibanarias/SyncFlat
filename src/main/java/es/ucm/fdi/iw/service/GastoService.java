package es.ucm.fdi.iw.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.ucm.fdi.iw.dto.DeudaNeta;
import es.ucm.fdi.iw.dto.GastoDetalle;
import es.ucm.fdi.iw.dto.GastoForm;
import es.ucm.fdi.iw.model.EstadoGasto;
import es.ucm.fdi.iw.model.Gasto;
import es.ucm.fdi.iw.model.MiembroPiso;
import es.ucm.fdi.iw.model.ParticipanteGasto;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.TipoAlerta;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;

/**
 * Lógica de negocio del módulo de Gastos.
 * <p>
 * Centraliza la creación de gastos, el reparto entre miembros y
 * el ciclo de pago hasta la liquidación.
 */
@RequiredArgsConstructor
@Service
public class GastoService {

    private static final Logger log = LogManager.getLogger(GastoService.class);

    private final EntityManager entityManager;
    private final AlertaService alertaService;

    /**
     * Devuelve los {@code max} gastos más recientes del piso.
     * Usado por el dashboard de home para mostrar el resumen.
     */
    @Transactional(readOnly = true)
    public List<Gasto> obtenerRecientes(long pisoId, int max) {
        return entityManager
            .createQuery("SELECT g FROM Gasto g WHERE g.piso.id = :pid ORDER BY g.fecha DESC", Gasto.class)
            .setParameter("pid", pisoId)
            .setMaxResults(max)
            .getResultList();
    }

    /** Devuelve los gastos del piso ordenados por fecha descendente. */
    @Transactional(readOnly = true)
    public List<Gasto> obtenerGastos(long pisoId) {
        return entityManager
            .createQuery("SELECT g FROM Gasto g WHERE g.piso.id = :pid ORDER BY g.fecha DESC", Gasto.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve todos los participantes de los gastos del piso. */
    @Transactional(readOnly = true)
    public List<ParticipanteGasto> obtenerParticipantes(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT pg FROM ParticipanteGasto pg WHERE pg.gasto.piso.id = :pid",
                ParticipanteGasto.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve los miembros activos del piso (sin fecha de salida). */
    @Transactional(readOnly = true)
    public List<MiembroPiso> obtenerMiembrosActivos(long pisoId) {
        return cargarMiembrosActivos(pisoId);
    }

    /**
     * Devuelve los 10 gastos más recientes del piso con sus participaciones agrupadas.
     * Se emite una sola consulta extra para las participaciones (evita N+1).
     */
    @Transactional(readOnly = true)
    public List<GastoDetalle> obtenerHistorial(long pisoId) {
        List<Gasto> gastos = entityManager
            .createQuery("SELECT g FROM Gasto g WHERE g.piso.id = :pid ORDER BY g.fecha DESC",
                Gasto.class)
            .setParameter("pid", pisoId)
            .setMaxResults(10)
            .getResultList();
        if (gastos.isEmpty()) return List.of();

        List<Long> gastoIds = gastos.stream().map(Gasto::getId).toList();
        List<ParticipanteGasto> todos = entityManager
            .createQuery("SELECT pg FROM ParticipanteGasto pg WHERE pg.gasto.id IN :ids",
                ParticipanteGasto.class)
            .setParameter("ids", gastoIds)
            .getResultList();

        Map<Long, List<ParticipanteGasto>> porGasto = new HashMap<>();
        for (ParticipanteGasto pg : todos) {
            porGasto.computeIfAbsent(pg.getGasto().getId(), k -> new ArrayList<>()).add(pg);
        }
        return gastos.stream()
            .map(g -> new GastoDetalle(g, porGasto.getOrDefault(g.getId(), List.of())))
            .toList();
    }

    /**
     * Participaciones pendientes donde {@code usuarioId} es deudor (no es el pagador del gasto).
     * Representa "lo que debo" desde el punto de vista del usuario logueado.
     */
    @Transactional(readOnly = true)
    public List<ParticipanteGasto> obtenerLoDebo(long pisoId, long usuarioId) {
        return entityManager
            .createQuery(
                "SELECT pg FROM ParticipanteGasto pg " +
                "WHERE pg.gasto.piso.id = :pid AND pg.pagado = false " +
                "AND pg.usuario.id = :uid AND pg.gasto.pagador.id <> :uid",
                ParticipanteGasto.class)
            .setParameter("pid", pisoId)
            .setParameter("uid", usuarioId)
            .getResultList();
    }

    /**
     * Participaciones pendientes de otros usuarios en gastos cuyo pagador es {@code usuarioId}.
     * Representa "lo que me deben" desde el punto de vista del usuario logueado.
     */
    @Transactional(readOnly = true)
    public List<ParticipanteGasto> obtenerMeDeben(long pisoId, long usuarioId) {
        return entityManager
            .createQuery(
                "SELECT pg FROM ParticipanteGasto pg " +
                "WHERE pg.gasto.piso.id = :pid AND pg.pagado = false " +
                "AND pg.gasto.pagador.id = :uid AND pg.usuario.id <> :uid",
                ParticipanteGasto.class)
            .setParameter("pid", pisoId)
            .setParameter("uid", usuarioId)
            .getResultList();
    }

    /**
     * Calcula el balance neto de deudas entre los miembros del piso.
     * <p>
     * Para cada par de usuarios (A, B), compensa lo que A debe a B con lo que B debe a A;
     * solo se incluye en el resultado el par si queda un saldo positivo tras la compensación.
     * <p>
     * Se tienen en cuenta únicamente las participaciones cuyo {@code pagado = false}.
     *
     * @param pisoId identificador del piso
     * @return lista de deudas netas; vacía si todos los gastos están saldados
     */
    @Transactional(readOnly = true)
    public List<DeudaNeta> calcularBalanceNeto(long pisoId) {
        List<ParticipanteGasto> pendientes = entityManager
            .createQuery(
                "SELECT pg FROM ParticipanteGasto pg " +
                "WHERE pg.gasto.piso.id = :pid AND pg.pagado = false",
                ParticipanteGasto.class)
            .setParameter("pid", pisoId)
            .getResultList();

        // bruto.get(deudorId).get(acreedorId) = suma de importes pendientes
        Map<Long, Map<Long, BigDecimal>> bruto = new HashMap<>();
        Map<Long, User> usuarios = new HashMap<>();

        for (ParticipanteGasto pg : pendientes) {
            User deudor   = pg.getUsuario();
            User acreedor = pg.getGasto().getPagador();
            if (deudor.getId() == acreedor.getId()) continue;

            usuarios.put(deudor.getId(),   deudor);
            usuarios.put(acreedor.getId(), acreedor);
            bruto.computeIfAbsent(deudor.getId(), k -> new HashMap<>())
                 .merge(acreedor.getId(), pg.getImporteAsignado(), BigDecimal::add);
        }

        List<Long> ids = new ArrayList<>(usuarios.keySet());
        List<DeudaNeta> resultado = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                long idA = ids.get(i);
                long idB = ids.get(j);
                BigDecimal aDebeB = bruto.getOrDefault(idA, Map.of()).getOrDefault(idB, BigDecimal.ZERO);
                BigDecimal bDebeA = bruto.getOrDefault(idB, Map.of()).getOrDefault(idA, BigDecimal.ZERO);
                BigDecimal neto   = aDebeB.subtract(bDebeA);
                int cmp = neto.compareTo(BigDecimal.ZERO);
                if (cmp > 0) {
                    resultado.add(new DeudaNeta(usuarios.get(idA), usuarios.get(idB), neto));
                } else if (cmp < 0) {
                    resultado.add(new DeudaNeta(usuarios.get(idB), usuarios.get(idA), neto.negate()));
                }
                // cmp == 0: saldo compensado, no se incluye
            }
        }
        return resultado;
    }

    /**
     * Crea un gasto y reparte su importe a partes iguales entre los participantes seleccionados.
     * <p>
     * Si {@code form.getParticipanteIds()} está vacío (ningún checkbox marcado), el reparto
     * recae sobre todos los miembros activos del piso (fallback explícito, no silencioso).
     * Los IDs recibidos que no correspondan a miembros activos del piso son ignorados.
     * El pagador queda marcado con {@code pagado = true} si aparece en el reparto.
     *
     * @param form      datos validados del formulario (incluye participanteIds)
     * @param pagadorId identificador del usuario que paga
     * @param piso      piso al que pertenece el gasto
     */
    @Transactional
    public void crearGasto(GastoForm form, long pagadorId, Piso piso) {
        User pagador = entityManager.find(User.class, pagadorId);

        Gasto g = new Gasto();
        g.setConcepto(form.getConcepto().trim());
        g.setImporte(form.getImporte());
        g.setFecha(LocalDate.now());
        g.setPagador(pagador);
        g.setPiso(piso);
        g.setEstado(EstadoGasto.PENDIENTE);
        entityManager.persist(g);
        entityManager.flush();

        List<MiembroPiso> miembrosActivos = cargarMiembrosActivos(piso.getId());
        List<User> aRepartir = resolverParticipantes(form.getParticipanteIds(), miembrosActivos);

        if (!aRepartir.isEmpty()) {
            BigDecimal cuota = form.getImporte()
                .divide(BigDecimal.valueOf(aRepartir.size()), 2, RoundingMode.HALF_UP);
            for (User u : aRepartir) {
                ParticipanteGasto pg = new ParticipanteGasto();
                pg.setGasto(g);
                pg.setUsuario(u);
                pg.setImporteAsignado(cuota);
                pg.setPagado(u.getId() == pagadorId);
                entityManager.persist(pg);
            }
        }
        alertaService.crearAlerta(
            "Nuevo gasto: " + g.getConcepto() + " — " + g.getImporte() + " €",
            TipoAlerta.URGENTE, piso);
        log.info("Gasto '{}' creado por {} — {} €, repartido entre {} participante(s)",
            g.getConcepto(), pagador.getUsername(), g.getImporte(), aRepartir.size());
    }

    /**
     * Determina la lista de usuarios entre los que se reparte el gasto.
     * Si la selección está vacía o ninguno es miembro activo, usa todos los miembros activos.
     */
    private List<User> resolverParticipantes(List<Long> seleccionados, List<MiembroPiso> miembrosActivos) {
        if (seleccionados == null || seleccionados.isEmpty()) {
            log.debug("Sin participantes seleccionados; fallback a todos los miembros activos");
            return miembrosActivos.stream().map(MiembroPiso::getUsuario).toList();
        }
        Set<Long> idsActivos = miembrosActivos.stream()
            .map(mp -> mp.getUsuario().getId())
            .collect(Collectors.toSet());
        List<User> filtrados = seleccionados.stream()
            .filter(idsActivos::contains)
            .map(id -> entityManager.find(User.class, id))
            .filter(Objects::nonNull)
            .toList();
        if (filtrados.isEmpty()) {
            log.warn("Ningún participanteId válido recibido; fallback a todos los miembros activos");
            return miembrosActivos.stream().map(MiembroPiso::getUsuario).toList();
        }
        return filtrados;
    }

    /**
     * Marca la participación como pagada y liquida el gasto si ya no quedan pendientes.
     * <p>
     * Invariante: el pagador del gasto no puede pagarse a sí mismo. Su participación
     * siempre nace con {@code pagado = true} en {@link #crearGasto}; esta guarda evita
     * que datos corruptos o llamadas directas a la API rompan esa invariante.
     *
     * @param participanteId identificador del {@link ParticipanteGasto}
     * @param usuarioId      usuario que realiza la acción
     * @return {@code true} si el gasto ha quedado liquidado tras el pago
     * @throws IllegalArgumentException si la participación no existe
     * @throws SecurityException        si el usuario no es el propietario de la participación
     * @throws IllegalStateException    si la participación ya estaba pagada o el usuario es el pagador
     */
    @Transactional
    public boolean pagarParticipacion(long participanteId, long usuarioId) {
        ParticipanteGasto pg = entityManager.find(ParticipanteGasto.class, participanteId);
        if (pg == null) throw new IllegalArgumentException("participación no encontrada");
        if (pg.getUsuario().getId() != usuarioId) throw new SecurityException("no autorizado");
        if (pg.getUsuario().getId() == pg.getGasto().getPagador().getId())
            throw new IllegalStateException(
                "El pagador del gasto no puede pagarse a sí mismo. Su parte queda saldada automáticamente.");
        if (pg.isPagado()) throw new IllegalStateException("ya estaba pagado");

        pg.setPagado(true);

        long pendientes = entityManager
            .createQuery(
                "SELECT COUNT(p) FROM ParticipanteGasto p WHERE p.gasto.id = :gid AND p.pagado = false",
                Long.class)
            .setParameter("gid", pg.getGasto().getId())
            .getSingleResult();

        boolean liquidado = pendientes == 0;
        if (liquidado) pg.getGasto().setEstado(EstadoGasto.LIQUIDADO);

        log.info("Participante marcó su parte del gasto '{}' como pagada{}",
            pg.getGasto().getConcepto(), liquidado ? " — gasto liquidado" : "");
        return liquidado;
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
