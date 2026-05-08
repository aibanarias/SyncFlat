package es.ucm.fdi.iw.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.ucm.fdi.iw.dto.TareaForm;
import es.ucm.fdi.iw.model.AsignacionTarea;
import es.ucm.fdi.iw.model.FrecuenciaTarea;
import es.ucm.fdi.iw.model.MiembroPiso;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.Tarea;
import es.ucm.fdi.iw.model.TipoAlerta;
import es.ucm.fdi.iw.model.TipoTarea;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;

/**
 * Lógica de negocio del módulo de Tareas.
 *
 * <p>Cada {@link AsignacionTarea} sigue tres estados secuenciales:
 * <ol>
 *   <li><b>PENDIENTE</b>: {@code fechaCompletada} es nula.</li>
 *   <li><b>COMPLETADA</b>: {@code fechaCompletada} tiene valor; {@code validada = false}.</li>
 *   <li><b>VALIDADA</b>: {@code validada = true} y {@code validador} registrado.</li>
 * </ol>
 *
 * <p>Reglas de validación:
 * <ul>
 *   <li>Solo se puede validar una tarea ya completada.</li>
 *   <li>El usuario asignado no puede validar su propia tarea.</li>
 *   <li>Una tarea validada no puede reabrirse ni validarse de nuevo.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Service
public class TareaService {

    private static final Logger log = LogManager.getLogger(TareaService.class);

    private final EntityManager entityManager;
    private final AlertaService alertaService;

    // -------------------------------------------------------------------------
    // Consultas de solo lectura
    // -------------------------------------------------------------------------

    /**
     * Devuelve las primeras {@code max} asignaciones pendientes del piso, ordenadas por fecha límite.
     * Usado por el dashboard de home para mostrar el resumen.
     */
    @Transactional(readOnly = true)
    public List<AsignacionTarea> obtenerPendientesResumen(long pisoId, int max) {
        return entityManager
            .createQuery(
                "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid"
                + " AND at.fechaCompletada IS NULL ORDER BY at.tarea.fechaLimite",
                AsignacionTarea.class)
            .setParameter("pid", pisoId)
            .setMaxResults(max)
            .getResultList();
    }

    /** Asignaciones pendientes (fechaCompletada nula), ordenadas por fecha límite. */
    @Transactional(readOnly = true)
    public List<AsignacionTarea> obtenerPendientes(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid"
                + " AND at.fechaCompletada IS NULL ORDER BY at.tarea.fechaLimite ASC",
                AsignacionTarea.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Asignaciones completadas pero aún sin validar, ordenadas por fecha de completado. */
    @Transactional(readOnly = true)
    public List<AsignacionTarea> obtenerPendientesDeValidar(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid"
                + " AND at.fechaCompletada IS NOT NULL AND at.validada = false"
                + " ORDER BY at.fechaCompletada DESC",
                AsignacionTarea.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Últimas 10 asignaciones validadas del piso, ordenadas por fecha de completado descendente. */
    @Transactional(readOnly = true)
    public List<AsignacionTarea> obtenerValidadas(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT at FROM AsignacionTarea at WHERE at.tarea.piso.id = :pid"
                + " AND at.validada = true ORDER BY at.fechaCompletada DESC",
                AsignacionTarea.class)
            .setParameter("pid", pisoId)
            .setMaxResults(10)
            .getResultList();
    }

    /** Tareas sin ninguna asignación activa, ordenadas por fecha límite. */
    @Transactional(readOnly = true)
    public List<Tarea> obtenerSinAsignar(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT t FROM Tarea t WHERE t.piso.id = :pid"
                + " AND NOT EXISTS (SELECT at FROM AsignacionTarea at WHERE at.tarea.id = t.id)"
                + " ORDER BY t.fechaLimite ASC",
                Tarea.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Miembros activos del piso (sin fecha de salida). */
    @Transactional(readOnly = true)
    public List<MiembroPiso> obtenerMiembrosActivos(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    // -------------------------------------------------------------------------
    // Operaciones de escritura
    // -------------------------------------------------------------------------

    /**
     * Crea una tarea en el piso y, si se indica, genera la asignación inicial.
     */
    @Transactional
    public void crearTarea(TareaForm form, Piso piso) {
        Tarea t = new Tarea();
        t.setNombre(form.getNombre().trim());
        t.setDescripcion(form.getDescripcion());
        t.setTipo(TipoTarea.valueOf(form.getTipo()));
        if (form.getFrecuencia() != null && !form.getFrecuencia().isBlank()) {
            t.setFrecuencia(FrecuenciaTarea.valueOf(form.getFrecuencia()));
        }
        t.setFechaLimite(LocalDate.parse(form.getFechaLimite()));
        t.setPiso(piso);
        entityManager.persist(t);
        entityManager.flush();

        if (form.getAsignadoId() != null) {
            AsignacionTarea at = new AsignacionTarea();
            at.setTarea(t);
            User asignado = entityManager.find(User.class, form.getAsignadoId());
            at.setUsuario(asignado);
            at.setFechaAsignacion(LocalDate.now());
            at.setValidada(false);
            entityManager.persist(at);
            alertaService.crearAlerta(
                "Nueva tarea asignada a " + asignado.getUsername() + ": " + t.getNombre(),
                TipoAlerta.INFO, piso);
        } else {
            alertaService.crearAlerta("Nueva tarea: " + t.getNombre(), TipoAlerta.INFO, piso);
        }
        log.info("Tarea '{}' creada en piso {}", t.getNombre(), piso.getNombre());
    }

    /**
     * Alterna el estado de completado de una asignación: PENDIENTE ↔ COMPLETADA.
     *
     * <p>No se puede reabrir una tarea ya {@code validada}.
     *
     * @param asignacionId identificador de la asignación
     * @param pisoId       piso del solicitante — guard de seguridad
     * @return mapa con las claves {@code completada} y {@code validada}
     * @throws IllegalArgumentException si la asignación no existe
     * @throws SecurityException        si la asignación pertenece a otro piso
     * @throws IllegalStateException    si la tarea está validada y se intenta reabrir
     */
    @Transactional
    public Map<String, Object> completarTarea(long asignacionId, long pisoId) {
        AsignacionTarea at = entityManager.find(AsignacionTarea.class, asignacionId);
        if (at == null) throw new IllegalArgumentException("Asignación no encontrada.");
        if (at.getTarea().getPiso().getId() != pisoId)
            throw new SecurityException("La asignación no pertenece a tu piso.");

        if (at.isValidada()) {
            throw new IllegalStateException("No se puede reabrir una tarea ya validada.");
        }
        if (at.getFechaCompletada() == null) {
            at.setFechaCompletada(LocalDate.now());
            log.info("Tarea '{}' marcada como completada", at.getTarea().getNombre());
        } else {
            at.setFechaCompletada(null);
            log.info("Tarea '{}' reabierta", at.getTarea().getNombre());
        }
        return Map.of("completada", at.getFechaCompletada() != null, "validada", at.isValidada());
    }

    /**
     * Valida una asignación de tarea completada.
     *
     * <p>Condiciones previas:
     * <ul>
     *   <li>La tarea debe estar en estado COMPLETADA ({@code fechaCompletada} no nula).</li>
     *   <li>No puede estar ya validada.</li>
     *   <li>El validador no puede ser el mismo usuario que realizó la tarea.</li>
     * </ul>
     *
     * @param asignacionId id de la asignación a validar
     * @param validador    usuario que realiza la validación
     * @param pisoId       piso del solicitante — guard de seguridad
     * @return mapa con {@code validada} (true) y {@code validador} (username)
     * @throws IllegalArgumentException si la asignación no existe
     * @throws SecurityException        si la asignación pertenece a otro piso
     * @throws IllegalStateException    si no se cumplen las condiciones previas
     */
    @Transactional
    public Map<String, Object> validarTarea(long asignacionId, User validador, long pisoId) {
        AsignacionTarea at = entityManager.find(AsignacionTarea.class, asignacionId);
        if (at == null) throw new IllegalArgumentException("Asignación no encontrada.");
        if (at.getTarea().getPiso().getId() != pisoId)
            throw new SecurityException("La asignación no pertenece a tu piso.");

        if (at.getFechaCompletada() == null) {
            throw new IllegalStateException("La tarea debe completarse antes de validarla.");
        }
        if (at.isValidada()) {
            throw new IllegalStateException("Esta tarea ya está validada.");
        }
        if (at.getUsuario().getId() == validador.getId()) {
            throw new IllegalStateException("No puedes validar una tarea que tú mismo completaste.");
        }

        at.setValidada(true);
        at.setValidador(entityManager.find(User.class, validador.getId()));
        log.info("Tarea '{}' validada por {}", at.getTarea().getNombre(), validador.getUsername());

        boolean esRecurrente = at.getTarea().getTipo() == TipoTarea.RECURRENTE
            && at.getTarea().getFrecuencia() != null;
        if (esRecurrente) {
            generarSiguienteOcurrencia(at);
        }
        return Map.of("validada", true, "validador", validador.getUsername(), "nuevaOcurrencia", esRecurrente);
    }

    private void generarSiguienteOcurrencia(AsignacionTarea completada) {
        Tarea original = completada.getTarea();
        Tarea siguiente = new Tarea();
        siguiente.setNombre(original.getNombre());
        siguiente.setDescripcion(original.getDescripcion());
        siguiente.setTipo(original.getTipo());
        siguiente.setFrecuencia(original.getFrecuencia());
        siguiente.setFechaLimite(original.getFrecuencia().siguienteFechaLimite(original.getFechaLimite()));
        siguiente.setPiso(original.getPiso());
        entityManager.persist(siguiente);
        entityManager.flush();

        AsignacionTarea at = new AsignacionTarea();
        at.setTarea(siguiente);
        at.setUsuario(completada.getUsuario());
        at.setFechaAsignacion(LocalDate.now());
        at.setValidada(false);
        entityManager.persist(at);

        alertaService.crearAlerta(
            "Nueva ocurrencia de tarea: " + siguiente.getNombre(),
            TipoAlerta.INFO, original.getPiso());
        log.info("Ocurrencia siguiente de '{}' generada para {} — fecha límite {}",
            siguiente.getNombre(), completada.getUsuario().getUsername(), siguiente.getFechaLimite());
    }
}
