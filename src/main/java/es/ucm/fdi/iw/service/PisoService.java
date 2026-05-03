package es.ucm.fdi.iw.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.ucm.fdi.iw.dto.CrearPisoForm;
import es.ucm.fdi.iw.model.MiembroPiso;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.RolPiso;
import es.ucm.fdi.iw.model.TipoAlerta;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;

/**
 * Centraliza toda la lógica de negocio relacionada con la pertenencia de un
 * usuario a un piso: resolución del piso activo, creación, adhesión, abandono
 * y administración.
 *
 * <p>Reglas invariantes:
 * <ul>
 *   <li>Un usuario solo puede tener una membresía activa a la vez.</li>
 *   <li>El código de invitación de cada piso es único.</li>
 *   <li>Abandonar el piso nunca borra el histórico; solo fija {@code fechaSalida}.</li>
 *   <li>Si el último ADMIN abandona y quedan miembros, el miembro más antiguo es promovido.</li>
 *   <li>Si el último miembro activo abandona, el piso y todos sus datos se eliminan.</li>
 *   <li>Una habitación solo puede estar asignada a un miembro activo a la vez.</li>
 *   <li>Solo los ADMIN pueden configurar el número total de habitaciones.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PisoService {

    private static final Logger log = LogManager.getLogger(PisoService.class);

    private static final String CODIGO_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODIGO_LONGITUD = 6;

    /** Describe el resultado de una operación de abandono para que el controlador pueda informar al usuario. */
    public enum AbandonoResultado {
        /** El usuario abandonó; el piso sigue activo con otros miembros. */
        ABANDONADO,
        /** El usuario era el último ADMIN; se ha promovido automáticamente a otro miembro. */
        ABANDONADO_REASIGNACION,
        /** Era el último miembro activo; el piso ha sido eliminado. */
        ABANDONADO_PISO_ELIMINADO
    }

    private final EntityManager entityManager;
    private final AlertaService alertaService;

    // -------------------------------------------------------------------------
    // Consultas de solo lectura
    // -------------------------------------------------------------------------

    /**
     * Devuelve la membresía activa del usuario en sesión, si existe.
     */
    public Optional<MiembroPiso> resolverMiembroPiso(HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null) return Optional.empty();
        return entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.usuario.id = :uid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("uid", u.getId())
            .getResultStream()
            .findFirst();
    }

    /**
     * Devuelve el piso activo del usuario en sesión, o {@code null}.
     */
    public Piso resolverPiso(HttpSession session) {
        return resolverMiembroPiso(session).map(MiembroPiso::getPiso).orElse(null);
    }

    /**
     * Devuelve los miembros activos del piso, ordenados por fecha de ingreso.
     */
    public List<MiembroPiso> obtenerMiembrosActivos(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL ORDER BY mp.fechaIngreso",
                MiembroPiso.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /**
     * Construye el mapa de ocupación de habitaciones del piso.
     * <p>
     * Para cada número de habitación (1..{@code piso.numHabitaciones}) asigna el
     * {@link MiembroPiso} que la ocupa, o {@code null} si está libre.
     * El mapa está ordenado por número de habitación ascendente.
     *
     * @param piso     piso cuyo número total de habitaciones se usará como rango
     * @param miembros miembros activos del piso (ya cargados)
     * @return mapa ordenado habitación → miembro ocupante ({@code null} si libre);
     *         mapa vacío si el piso no tiene número de habitaciones configurado
     */
    public Map<Integer, MiembroPiso> construirMapaOcupacion(Piso piso, List<MiembroPiso> miembros) {
        if (piso.getNumHabitaciones() == null) return Map.of();
        Map<Integer, MiembroPiso> ocupacion = new LinkedHashMap<>();
        for (int i = 1; i <= piso.getNumHabitaciones(); i++) {
            ocupacion.put(i, null);
        }
        for (MiembroPiso mp : miembros) {
            if (mp.getNumHabitacion() != null && mp.getNumHabitacion() <= piso.getNumHabitaciones()) {
                ocupacion.put(mp.getNumHabitacion(), mp);
            }
        }
        return ocupacion;
    }

    /**
     * Devuelve la membresía activa del usuario en el piso, o {@code null} si no existe.
     */
    public MiembroPiso obtenerMembresia(long pisoId, long userId) {
        return obtenerMiembriaActiva(userId, pisoId).orElse(null);
    }

    /**
     * Cuenta el número de habitaciones distintas ocupadas por los miembros activos proporcionados.
     */
    public int contarHabitacionesOcupadas(List<MiembroPiso> miembros) {
        return (int) miembros.stream()
            .filter(mp -> mp.getNumHabitacion() != null)
            .map(MiembroPiso::getNumHabitacion)
            .distinct()
            .count();
    }

    // -------------------------------------------------------------------------
    // Operaciones de escritura: ciclo de vida del piso
    // -------------------------------------------------------------------------

    /**
     * Crea un nuevo piso y asigna al creador como {@link RolPiso#ADMIN}.
     *
     * @throws IllegalStateException si el usuario ya pertenece a un piso activo
     */
    @Transactional
    public Piso crearPiso(CrearPisoForm form, User creador) {
        verificarSinPisoActivo(creador.getId());

        Piso piso = new Piso();
        piso.setNombre(form.getNombre());
        piso.setDireccion(form.getDireccion());
        piso.setFechaCreacion(LocalDate.now());
        piso.setCodigoInvitacion(generarCodigoUnico());
        entityManager.persist(piso);

        MiembroPiso miembro = new MiembroPiso();
        miembro.setPiso(piso);
        miembro.setUsuario(entityManager.find(User.class, creador.getId()));
        miembro.setRolEnPiso(RolPiso.ADMIN);
        miembro.setFechaIngreso(LocalDate.now());
        entityManager.persist(miembro);

        log.info("Piso '{}' creado por {} (ADMIN) con código {}",
            piso.getNombre(), creador.getUsername(), piso.getCodigoInvitacion());
        return piso;
    }

    /**
     * Une al usuario a un piso existente como {@link RolPiso#MIEMBRO}.
     *
     * @throws IllegalArgumentException si el código no corresponde a ningún piso
     * @throws IllegalStateException    si el usuario ya pertenece a un piso activo
     */
    @Transactional
    public void unirse(String codigo, User usuario) {
        String codigoNorm = codigo.trim().toUpperCase();
        List<Piso> pisos = entityManager
            .createQuery("SELECT p FROM Piso p WHERE p.codigoInvitacion = :c", Piso.class)
            .setParameter("c", codigoNorm)
            .getResultList();
        if (pisos.isEmpty()) {
            throw new IllegalArgumentException("Código de invitación no válido.");
        }
        verificarSinPisoActivo(usuario.getId());

        MiembroPiso miembro = new MiembroPiso();
        miembro.setPiso(pisos.get(0));
        miembro.setUsuario(entityManager.find(User.class, usuario.getId()));
        miembro.setRolEnPiso(RolPiso.MIEMBRO);
        miembro.setFechaIngreso(LocalDate.now());
        entityManager.persist(miembro);

        alertaService.crearAlerta(
            usuario.getUsername() + " se ha unido al piso",
            TipoAlerta.INFO, pisos.get(0));
        log.info("{} se ha unido al piso '{}' (código {})",
            usuario.getUsername(), pisos.get(0).getNombre(), codigoNorm);
    }

    /**
     * Gestiona el abandono del piso activo del usuario.
     *
     * <p>Comportamiento:
     * <ol>
     *   <li>Fija {@code fechaSalida} en la membresía activa.</li>
     *   <li>Si era el último miembro, elimina el piso y todos sus datos.</li>
     *   <li>Si era el único ADMIN y quedan miembros, promueve al miembro más antiguo.</li>
     * </ol>
     *
     * @return resultado que describe lo que ocurrió tras el abandono
     * @throws IllegalStateException si el usuario no está en ningún piso activo
     */
    @Transactional
    public AbandonoResultado abandonar(User usuario) {
        List<MiembroPiso> activos = entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.usuario.id = :uid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("uid", usuario.getId())
            .getResultList();
        if (activos.isEmpty()) {
            throw new IllegalStateException("No perteneces a ningún piso activo.");
        }

        MiembroPiso mp = activos.get(0);
        long pisoId = mp.getPiso().getId();
        String nombrePiso = mp.getPiso().getNombre();
        RolPiso rolSaliente = mp.getRolEnPiso();

        mp.setFechaSalida(LocalDate.now());
        // El auto-flush de JPA escribe fechaSalida antes de la siguiente query
        List<MiembroPiso> restantes = entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("pid", pisoId)
            .getResultList();

        if (restantes.isEmpty()) {
            entityManager.clear();
            eliminarPiso(pisoId);
            log.info("{} era el último miembro; piso '{}' eliminado", usuario.getUsername(), nombrePiso);
            return AbandonoResultado.ABANDONADO_PISO_ELIMINADO;
        }

        if (rolSaliente == RolPiso.ADMIN) {
            boolean hayOtroAdmin = restantes.stream()
                .anyMatch(m -> m.getRolEnPiso() == RolPiso.ADMIN);
            if (!hayOtroAdmin) {
                // Desempate por id asc para que el resultado sea determinista
                // incluso cuando dos miembros comparten la misma fechaIngreso.
                MiembroPiso nuevo = restantes.stream()
                    .min(Comparator.comparing(MiembroPiso::getFechaIngreso)
                        .thenComparingLong(MiembroPiso::getId))
                    .orElseThrow();
                nuevo.setRolEnPiso(RolPiso.ADMIN);
                log.info("{} promovido automáticamente a ADMIN en '{}' tras la salida de {}",
                    nuevo.getUsuario().getUsername(), nombrePiso, usuario.getUsername());
                return AbandonoResultado.ABANDONADO_REASIGNACION;
            }
        }

        log.info("{} ha abandonado el piso '{}'", usuario.getUsername(), nombrePiso);
        return AbandonoResultado.ABANDONADO;
    }

    // -------------------------------------------------------------------------
    // Operaciones de escritura: habitaciones
    // -------------------------------------------------------------------------

    /**
     * Asigna o actualiza el número de habitación del miembro activo.
     *
     * <p>Validaciones:
     * <ul>
     *   <li>El número no puede superar el total configurado en el piso.</li>
     *   <li>La habitación no puede estar ya ocupada por otro miembro activo.</li>
     * </ul>
     *
     * @throws IllegalStateException    si el usuario no pertenece a ningún piso activo
     * @throws IllegalArgumentException si la habitación está ocupada o fuera de rango
     */
    @Transactional
    public void asignarHabitacion(User usuario, int numHabitacion) {
        MiembroPiso mp = entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.usuario.id = :uid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("uid", usuario.getId())
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No perteneces a ningún piso activo."));

        Integer total = mp.getPiso().getNumHabitaciones();
        if (total != null && numHabitacion > total) {
            throw new IllegalArgumentException(
                "El número de habitación no puede superar el total del piso (" + total + ").");
        }

        // Verificar que ningún otro miembro activo ya ocupa esa habitación
        List<MiembroPiso> ocupantes = entityManager
            .createQuery(
                "SELECT m FROM MiembroPiso m WHERE m.piso.id = :pid AND m.fechaSalida IS NULL " +
                "AND m.numHabitacion = :num AND m.usuario.id != :uid",
                MiembroPiso.class)
            .setParameter("pid", mp.getPiso().getId())
            .setParameter("num", numHabitacion)
            .setParameter("uid", usuario.getId())
            .getResultList();
        if (!ocupantes.isEmpty()) {
            throw new IllegalArgumentException(
                "La habitación " + numHabitacion + " ya está ocupada por "
                + ocupantes.get(0).getUsuario().getUsername() + ".");
        }

        mp.setNumHabitacion(numHabitacion);
        log.info("{} asignado a habitación {} en piso '{}'",
            usuario.getUsername(), numHabitacion, mp.getPiso().getNombre());
    }

    /**
     * Configura el número total de habitaciones del piso.
     * <p>
     * Solo los miembros con rol {@link RolPiso#ADMIN} pueden modificar este valor.
     *
     * @throws IllegalStateException    si el solicitante no es ADMIN del piso
     * @throws IllegalArgumentException si el piso no existe
     */
    @Transactional
    public void configurarHabitaciones(long pisoId, int numHabitaciones, User solicitante) {
        obtenerMiembriaActiva(solicitante.getId(), pisoId)
            .filter(mp -> mp.getRolEnPiso() == RolPiso.ADMIN)
            .orElseThrow(() -> new IllegalStateException(
                "Solo los administradores pueden configurar el número de habitaciones."));

        Piso piso = entityManager.find(Piso.class, pisoId);
        if (piso == null) throw new IllegalArgumentException("Piso no encontrado.");
        piso.setNumHabitaciones(numHabitaciones);
        log.info("Piso '{}' actualizado a {} habitaciones por {}",
            piso.getNombre(), numHabitaciones, solicitante.getUsername());
    }

    // -------------------------------------------------------------------------
    // Operaciones de escritura: administración
    // -------------------------------------------------------------------------

    /**
     * Promueve a un miembro activo al rol {@link RolPiso#ADMIN}.
     * <p>
     * Solo un ADMIN actual del mismo piso puede realizar esta acción.
     *
     * @param membresiaId id de la membresía a promover
     * @param solicitante usuario que solicita la promoción; debe ser ADMIN del piso
     * @throws IllegalArgumentException si la membresía no existe o está inactiva
     * @throws IllegalStateException    si el solicitante no es ADMIN del piso
     */
    @Transactional
    public void promoverAdmin(long membresiaId, User solicitante) {
        MiembroPiso objetivo = entityManager.find(MiembroPiso.class, membresiaId);
        if (objetivo == null || objetivo.getFechaSalida() != null) {
            throw new IllegalArgumentException("Membresía no encontrada o ya inactiva.");
        }
        long pisoId = objetivo.getPiso().getId();
        obtenerMiembriaActiva(solicitante.getId(), pisoId)
            .filter(mp -> mp.getRolEnPiso() == RolPiso.ADMIN)
            .orElseThrow(() -> new IllegalStateException(
                "Solo los administradores pueden promover a otros miembros."));

        objetivo.setRolEnPiso(RolPiso.ADMIN);
        log.info("{} promovido a ADMIN en piso '{}' por {}",
            objetivo.getUsuario().getUsername(), objetivo.getPiso().getNombre(), solicitante.getUsername());
    }

    // -------------------------------------------------------------------------
    // Métodos privados de apoyo
    // -------------------------------------------------------------------------

    private void verificarSinPisoActivo(long usuarioId) {
        long count = entityManager
            .createQuery(
                "SELECT COUNT(mp) FROM MiembroPiso mp WHERE mp.usuario.id = :uid AND mp.fechaSalida IS NULL",
                Long.class)
            .setParameter("uid", usuarioId)
            .getSingleResult();
        if (count > 0) {
            throw new IllegalStateException("Ya perteneces a un piso activo. Debes abandonarlo antes.");
        }
    }

    private Optional<MiembroPiso> obtenerMiembriaActiva(long usuarioId, long pisoId) {
        return entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.usuario.id = :uid AND mp.piso.id = :pid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("uid", usuarioId)
            .setParameter("pid", pisoId)
            .getResultStream()
            .findFirst();
    }

    /**
     * Elimina el piso y todos sus datos en el orden correcto de dependencia de FKs.
     * Debe llamarse tras {@code entityManager.clear()} para evitar conflictos con el
     * caché de primer nivel.
     */
    private void eliminarPiso(long pisoId) {
        entityManager.createQuery("DELETE FROM ItemListaCompra i WHERE i.lista IN (SELECT l FROM ListaCompra l WHERE l.piso.id = :pid)").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM Compra c WHERE c.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM ListaCompra l WHERE l.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM Producto p WHERE p.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM ParticipanteGasto pg WHERE pg.gasto IN (SELECT g FROM Gasto g WHERE g.piso.id = :pid)").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM Gasto g WHERE g.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM AsignacionTarea at WHERE at.tarea IN (SELECT t FROM Tarea t WHERE t.piso.id = :pid)").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM Tarea t WHERE t.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM AsistenciaEvento ae WHERE ae.evento IN (SELECT e FROM Evento e WHERE e.piso.id = :pid)").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM Evento e WHERE e.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM Alerta a WHERE a.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        entityManager.createQuery("DELETE FROM MiembroPiso mp WHERE mp.piso.id = :pid").setParameter("pid", pisoId).executeUpdate();
        Piso piso = entityManager.find(Piso.class, pisoId);
        if (piso != null) entityManager.remove(piso);
    }

    private String generarCodigoUnico() {
        SecureRandom random = new SecureRandom();
        String codigo;
        do {
            StringBuilder sb = new StringBuilder(CODIGO_LONGITUD);
            for (int i = 0; i < CODIGO_LONGITUD; i++) {
                sb.append(CODIGO_CHARS.charAt(random.nextInt(CODIGO_CHARS.length())));
            }
            codigo = sb.toString();
        } while (entityManager
            .createQuery("SELECT COUNT(p) FROM Piso p WHERE p.codigoInvitacion = :c", Long.class)
            .setParameter("c", codigo)
            .getSingleResult() > 0);
        return codigo;
    }
}
