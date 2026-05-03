package es.ucm.fdi.iw.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.ucm.fdi.iw.dto.BloqueForm;
import es.ucm.fdi.iw.dto.EventoForm;
import es.ucm.fdi.iw.model.AsistenciaEvento;
import es.ucm.fdi.iw.model.BloqueHorario;
import es.ucm.fdi.iw.model.EstadoAsistencia;
import es.ucm.fdi.iw.model.EstadoEvento;
import es.ucm.fdi.iw.model.Evento;
import es.ucm.fdi.iw.model.MiembroPiso;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.TipoAlerta;
import es.ucm.fdi.iw.model.TipoBloque;
import es.ucm.fdi.iw.model.TipoRecurrencia;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;

/**
 * Lógica de negocio del módulo de Calendario.
 * <p>
 * Gestiona la creación de eventos, el ciclo de asistencia y los bloques
 * horarios personales. La detección de conflictos compara franjas de los
 * miembros del piso con los eventos propuestos.
 */
@RequiredArgsConstructor
@Service
public class CalendarioService {

    private static final Logger log = LogManager.getLogger(CalendarioService.class);

    /** Número máximo de ocurrencias pre-generadas al crear un evento recurrente. */
    private static final int MAX_SERIE_EVENTOS = 12;

    /**
     * Paleta de colores por usuario. El índice se calcula como {@code userId % PALETA.length}.
     * Todos los colores soportan texto blanco con contraste suficiente.
     * El orden debe coincidir con la constante {@code PALETA_BLOQUES} del JS de calendario.html.
     */
    private static final String[] PALETA_BLOQUES = {
        "#1d4ed8", // indigo
        "#15803d", // green
        "#b91c1c", // red
        "#7c3aed", // violet
        "#0e7490", // teal
        "#be185d", // rose
        "#c2410c", // orange-red
        "#166534"  // dark-green
    };

    /** Emoji representativo por tipo de bloque (solo informativo, ya no condicionado el color). */
    private static final Map<String, String> EMOJI_TIPO = Map.of(
        "TRABAJO",       "💼",
        "CLASES",        "📚",
        "ENTRENAMIENTO", "🏃",
        "OTRO",          "📌"
    );

    private final EntityManager entityManager;
    private final AlertaService alertaService;

    // -----------------------------------------------------------------------
    // Eventos
    // -----------------------------------------------------------------------

    /**
     * Devuelve los próximos eventos del piso a partir de ahora, ordenados por fecha ascendente.
     * Usado por el dashboard de home para mostrar el resumen.
     */
    @Transactional(readOnly = true)
    public List<Evento> obtenerProximosEventos(long pisoId, int max) {
        return entityManager
            .createQuery(
                "SELECT e FROM Evento e WHERE e.piso.id = :pid AND e.fechaInicio >= :now ORDER BY e.fechaInicio",
                Evento.class)
            .setParameter("pid", pisoId)
            .setParameter("now", LocalDateTime.now())
            .setMaxResults(max)
            .getResultList();
    }

    /** Devuelve los eventos del piso ordenados por fecha de inicio descendente. */
    @Transactional(readOnly = true)
    public List<Evento> obtenerEventos(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT e FROM Evento e WHERE e.piso.id = :pid ORDER BY e.fechaInicio DESC",
                Evento.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve los eventos del piso filtrados por estado, ordenados por fecha de inicio. */
    @Transactional(readOnly = true)
    public List<Evento> obtenerEventosPorEstado(long pisoId, EstadoEvento estado) {
        return entityManager
            .createQuery(
                "SELECT e FROM Evento e WHERE e.piso.id = :pid AND e.estado = :estado ORDER BY e.fechaInicio",
                Evento.class)
            .setParameter("pid", pisoId)
            .setParameter("estado", estado)
            .getResultList();
    }

    /** Devuelve todas las asistencias de los eventos del piso. */
    @Transactional(readOnly = true)
    public List<AsistenciaEvento> obtenerAsistencias(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT ae FROM AsistenciaEvento ae WHERE ae.evento.piso.id = :pid",
                AsistenciaEvento.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /** Devuelve los miembros activos del piso (sin fecha de salida). */
    @Transactional(readOnly = true)
    public List<MiembroPiso> obtenerMiembrosActivos(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    /**
     * Feed completo para FullCalendar dentro de un rango de fechas.
     * <p>
     * Devuelve los eventos del piso cuyo inicio cae dentro del rango, y las
     * ocurrencias de bloques (expandiendo los recurrentes) que solapan con él.
     *
     * @param pisoId     piso del que se cargan los datos
     * @param usuarioId  usuario en sesión (para marcar {@code esMio} en bloques)
     * @param rangeStart inicio del rango visible (inclusive)
     * @param rangeEnd   fin del rango visible (inclusive)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerFeed(long pisoId, long usuarioId,
                                                  LocalDate rangeStart, LocalDate rangeEnd) {
        List<Map<String, Object>> feed = new ArrayList<>();
        LocalDateTime rsdt = rangeStart.atStartOfDay();
        LocalDateTime redt = rangeEnd.plusDays(1).atStartOfDay();

        // ── Eventos del piso (filtrados por rango) ──────────────────────────
        List<Evento> eventos = entityManager
            .createQuery(
                "SELECT e FROM Evento e WHERE e.piso.id = :pid " +
                "AND e.fechaInicio < :redt AND e.fechaFin > :rsdt ORDER BY e.fechaInicio",
                Evento.class)
            .setParameter("pid", pisoId)
            .setParameter("rsdt", rsdt)
            .setParameter("redt", redt)
            .getResultList();

        Map<Long, List<AsistenciaEvento>> asistPorEvento = new HashMap<>();
        if (!eventos.isEmpty()) {
            entityManager
                .createQuery("SELECT ae FROM AsistenciaEvento ae WHERE ae.evento.piso.id = :pid",
                    AsistenciaEvento.class)
                .setParameter("pid", pisoId).getResultList()
                .forEach(ae -> asistPorEvento
                    .computeIfAbsent(ae.getEvento().getId(), k -> new ArrayList<>()).add(ae));
        }

        // Miembros activos del piso para incluir a todos en la lista de asistencias
        List<MiembroPiso> miembrosActivos = entityManager
            .createQuery("SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
            .setParameter("pid", pisoId)
            .getResultList();

        for (Evento e : eventos) {
            String color = switch (e.getEstado()) {
                case PROPUESTO -> "#ffc107";
                case APROBADO  -> "#198754";
                case RECHAZADO -> "#dc3545";
            };
            String textColor = e.getEstado() == EstadoEvento.PROPUESTO ? "#000000" : "#ffffff";
            String emoji     = switch (e.getEstado()) {
                case PROPUESTO -> "⏳ ";
                case APROBADO  -> "✅ ";
                case RECHAZADO -> "❌ ";
            };

            // Mapa de usuarioId → estado de asistencia para este evento
            Map<Long, String> asistEstado = new HashMap<>();
            for (AsistenciaEvento ae : asistPorEvento.getOrDefault(e.getId(), List.of())) {
                asistEstado.put(ae.getUsuario().getId(), ae.getEstado().name());
            }

            // Lista completa: todos los miembros del piso con su estado (SIN_RESPONDER si no han contestado)
            List<Map<String, String>> asistList = new ArrayList<>();
            for (MiembroPiso mp : miembrosActivos) {
                String estado = asistEstado.getOrDefault(mp.getUsuario().getId(), "SIN_RESPONDER");
                Map<String, String> entry = new HashMap<>();
                entry.put("usuario", mp.getUsuario().getUsername());
                entry.put("nombre",  mp.getUsuario().getFirstName() + " " + mp.getUsuario().getLastName());
                entry.put("estado",  estado);
                asistList.add(entry);
            }

            String miAsistencia = asistEstado.get(usuarioId);

            Map<String, Object> props = new HashMap<>();
            props.put("tipo",        "EVENTO");
            props.put("estado",      e.getEstado().name());
            props.put("descripcion", e.getDescripcion() != null ? e.getDescripcion() : "");
            props.put("creador",     e.getCreador().getUsername());
            props.put("eventoId",    e.getId());
            props.put("asistencias", asistList);
            if (miAsistencia != null) props.put("miAsistencia", miAsistencia);
            TipoRecurrencia tr = e.getTipoRecurrencia();
            if (tr != null && tr != TipoRecurrencia.NINGUNA) {
                props.put("tipoRecurrencia", tr.name());
                props.put("serieId", e.getSerieId());
            }

            Map<String, Object> ev = new HashMap<>();
            ev.put("id",              "evento-" + e.getId());
            ev.put("title",           emoji + e.getTitulo());
            ev.put("start",           e.getFechaInicio().toString());
            ev.put("end",             e.getFechaFin().toString());
            ev.put("backgroundColor", color);
            ev.put("borderColor",     color);
            ev.put("textColor",       textColor);
            ev.put("extendedProps",   props);
            feed.add(ev);
        }

        // ── Bloques horarios de todos los miembros activos ──────────────────
        List<BloqueHorario> bloques = entityManager
            .createQuery(
                "SELECT bh FROM BloqueHorario bh " +
                "WHERE bh.usuario IN (SELECT mp.usuario FROM MiembroPiso mp " +
                "WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL) ORDER BY bh.inicio",
                BloqueHorario.class)
            .setParameter("pid", pisoId).getResultList();

        for (BloqueHorario bh : bloques) {
            boolean esMio = bh.getUsuario().getId() == usuarioId;
            TipoRecurrencia tipoRec = bh.getTipoRecurrencia() != null
                ? bh.getTipoRecurrencia() : TipoRecurrencia.NINGUNA;

            if (tipoRec == TipoRecurrencia.NINGUNA) {
                if (!bh.getFin().isBefore(rsdt) && !bh.getInicio().isAfter(redt)) {
                    feed.add(construirEntradaBloque(bh, bh.getInicio(), bh.getFin(), esMio, ""));
                }
            } else {
                List<LocalDateTime[]> ocurrencias = expandirBloqueEnRango(bh, rsdt, redt);
                for (int i = 0; i < ocurrencias.size(); i++) {
                    LocalDateTime[] slot = ocurrencias.get(i);
                    feed.add(construirEntradaBloque(bh, slot[0], slot[1], esMio, "-" + i));
                }
            }
        }

        return feed;
    }

    /**
     * Crea un evento en el piso.
     * <p>
     * Si el formulario indica recurrencia, se generan hasta {@value MAX_SERIE_EVENTOS}
     * ocurrencias individuales. Cada una tiene su propio ciclo PROPUESTO → APROBADO
     * y comparte el mismo {@code serieId} (ID del primer evento de la serie).
     *
     * @throws IllegalArgumentException si la fecha de fin no es posterior a la de inicio
     */
    @Transactional
    public void crearEvento(EventoForm form, long creadorId, Piso piso) {
        LocalDateTime inicio = parseDateTime(form.getFechaInicio());
        LocalDateTime fin    = parseDateTime(form.getFechaFin());
        if (!fin.isAfter(inicio))
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la de inicio");

        TipoRecurrencia tipoRec = parseTipoRecurrencia(form.getTipoRecurrencia());

        Evento e = new Evento();
        e.setTitulo(form.getTitulo().trim());
        e.setDescripcion(form.getDescripcion());
        e.setFechaInicio(inicio);
        e.setFechaFin(fin);
        e.setEstado(EstadoEvento.PROPUESTO);
        e.setCreador(entityManager.find(User.class, creadorId));
        e.setPiso(piso);
        e.setTipoRecurrencia(tipoRec);
        e.setDiasSemana(form.getDiasSemana());
        entityManager.persist(e);
        entityManager.flush(); // necesario para obtener el id

        if (tipoRec != TipoRecurrencia.NINGUNA) {
            e.setSerieId(e.getId()); // la cabeza de serie apunta a sí misma
            Duration duracion = Duration.between(inicio, fin);
            List<LocalDateTime> fechas = generarFechasParaSerie(inicio, tipoRec, form.getDiasSemana());
            for (LocalDateTime fechaOcurrencia : fechas) {
                if (fechaOcurrencia.isEqual(inicio)) continue; // ya creada
                Evento ocurrencia = new Evento();
                ocurrencia.setTitulo(e.getTitulo());
                ocurrencia.setDescripcion(e.getDescripcion());
                ocurrencia.setFechaInicio(fechaOcurrencia);
                ocurrencia.setFechaFin(fechaOcurrencia.plus(duracion));
                ocurrencia.setEstado(EstadoEvento.PROPUESTO);
                ocurrencia.setCreador(e.getCreador());
                ocurrencia.setPiso(piso);
                ocurrencia.setTipoRecurrencia(tipoRec);
                ocurrencia.setDiasSemana(e.getDiasSemana());
                ocurrencia.setSerieId(e.getId());
                entityManager.persist(ocurrencia);
            }
            log.info("Serie de {} '{}' creada: {} ocurrencias en piso {}",
                tipoRec, e.getTitulo(), fechas.size(), piso.getNombre());
        }

        alertaService.crearAlerta("Nuevo evento propuesto: " + e.getTitulo(), TipoAlerta.INFO, piso);
        log.info("Evento '{}' propuesto en piso {}", e.getTitulo(), piso.getNombre());
    }

    /**
     * Alterna la asistencia del usuario a un evento cíclicamente:
     * si no existe la crea como CONFIRMADO; si existe rota CONFIRMADO → RECHAZADO → PENDIENTE.
     *
     * @return nuevo estado de asistencia
     * @throws IllegalArgumentException si el evento no existe
     */
    @Transactional
    public EstadoAsistencia toggleAsistencia(long eventoId, long usuarioId) {
        List<AsistenciaEvento> existing = entityManager
            .createQuery(
                "SELECT ae FROM AsistenciaEvento ae WHERE ae.evento.id = :eid AND ae.usuario.id = :uid",
                AsistenciaEvento.class)
            .setParameter("eid", eventoId)
            .setParameter("uid", usuarioId)
            .getResultList();

        EstadoAsistencia nuevoEstado;
        if (existing.isEmpty()) {
            Evento evento = entityManager.find(Evento.class, eventoId);
            if (evento == null) throw new IllegalArgumentException("evento no encontrado");
            if (evento.getEstado() != EstadoEvento.APROBADO)
                throw new IllegalStateException("Solo se puede confirmar asistencia a eventos aprobados.");
            AsistenciaEvento ae = new AsistenciaEvento();
            ae.setEvento(evento);
            ae.setUsuario(entityManager.find(User.class, usuarioId));
            ae.setEstado(EstadoAsistencia.CONFIRMADO);
            entityManager.persist(ae);
            nuevoEstado = EstadoAsistencia.CONFIRMADO;
        } else {
            // Si el evento ya no está aprobado, no permitir cambios
            Evento evento = existing.get(0).getEvento();
            if (evento.getEstado() != EstadoEvento.APROBADO)
                throw new IllegalStateException("Solo se puede gestionar asistencia en eventos aprobados.");
            AsistenciaEvento ae = existing.get(0);
            nuevoEstado = switch (ae.getEstado()) {
                case PENDIENTE    -> EstadoAsistencia.CONFIRMADO;
                case CONFIRMADO   -> EstadoAsistencia.RECHAZADO;
                case RECHAZADO    -> EstadoAsistencia.PENDIENTE;
            };
            ae.setEstado(nuevoEstado);
        }

        log.info("Asistencia de usuario {} al evento {} -> {}", usuarioId, eventoId, nuevoEstado);
        return nuevoEstado;
    }

    // -----------------------------------------------------------------------
    // Aprobación de eventos
    // -----------------------------------------------------------------------

    /**
     * Aprueba un evento propuesto: PROPUESTO → APROBADO.
     *
     * @param eventoId identificador del evento
     * @param pisoId   piso del solicitante (guard de seguridad)
     * @throws IllegalArgumentException si el evento no existe o no pertenece al piso
     * @throws IllegalStateException    si el evento no está en estado PROPUESTO
     */
    @Transactional
    public EstadoEvento aprobarEvento(long eventoId, long pisoId) {
        Evento e = cargarEventoDelPiso(eventoId, pisoId);
        if (e.getEstado() != EstadoEvento.PROPUESTO)
            throw new IllegalStateException("Solo se pueden aprobar eventos en estado PROPUESTO.");
        e.setEstado(EstadoEvento.APROBADO);
        alertaService.crearAlerta("Evento aprobado: " + e.getTitulo(), TipoAlerta.INFO, e.getPiso());
        log.info("Evento '{}' aprobado en piso {}", e.getTitulo(), pisoId);
        return EstadoEvento.APROBADO;
    }

    /**
     * Rechaza un evento: PROPUESTO/APROBADO → RECHAZADO.
     *
     * @throws IllegalArgumentException si el evento no existe o no pertenece al piso
     * @throws IllegalStateException    si el evento ya está rechazado
     */
    @Transactional
    public EstadoEvento rechazarEvento(long eventoId, long pisoId) {
        Evento e = cargarEventoDelPiso(eventoId, pisoId);
        if (e.getEstado() == EstadoEvento.RECHAZADO)
            throw new IllegalStateException("El evento ya está rechazado.");
        e.setEstado(EstadoEvento.RECHAZADO);
        alertaService.crearAlerta("Evento rechazado: " + e.getTitulo(), TipoAlerta.INFO, e.getPiso());
        log.info("Evento '{}' rechazado en piso {}", e.getTitulo(), pisoId);
        return EstadoEvento.RECHAZADO;
    }

    /**
     * Reabre un evento rechazado: RECHAZADO → PROPUESTO.
     *
     * @throws IllegalArgumentException si el evento no existe o no pertenece al piso
     * @throws IllegalStateException    si el evento no está rechazado
     */
    @Transactional
    public EstadoEvento reabrirEvento(long eventoId, long pisoId) {
        Evento e = cargarEventoDelPiso(eventoId, pisoId);
        if (e.getEstado() != EstadoEvento.RECHAZADO)
            throw new IllegalStateException("Solo se pueden reabrir eventos rechazados.");
        e.setEstado(EstadoEvento.PROPUESTO);
        log.info("Evento '{}' reabierto como propuesta en piso {}", e.getTitulo(), pisoId);
        return EstadoEvento.PROPUESTO;
    }

    private Evento cargarEventoDelPiso(long eventoId, long pisoId) {
        Evento e = entityManager.find(Evento.class, eventoId);
        if (e == null) throw new IllegalArgumentException("Evento no encontrado.");
        if (e.getPiso().getId() != pisoId) throw new SecurityException("El evento no pertenece a este piso.");
        return e;
    }

    // -----------------------------------------------------------------------
    // Bloques horarios personales
    // -----------------------------------------------------------------------

    /** Devuelve los bloques horarios del usuario ordenados por inicio. */
    @Transactional(readOnly = true)
    public List<BloqueHorario> obtenerMisBloques(long usuarioId) {
        return entityManager
            .createQuery(
                "SELECT bh FROM BloqueHorario bh WHERE bh.usuario.id = :uid ORDER BY bh.inicio",
                BloqueHorario.class)
            .setParameter("uid", usuarioId)
            .getResultList();
    }

    /**
     * Crea un bloque horario personal para el usuario.
     * <p>
     * Si se indica recurrencia, se almacena la regla en el propio bloque; las ocurrencias
     * se generan en tiempo de lectura dentro del feed (no se crean filas adicionales).
     *
     * @throws IllegalArgumentException si la hora de fin no es posterior a la de inicio
     */
    @Transactional
    public void crearBloque(BloqueForm form, User usuario) {
        LocalDateTime inicio = parseDateTime(form.getInicio());
        LocalDateTime fin    = parseDateTime(form.getFin());
        if (!fin.isAfter(inicio))
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio");
        if ("OTRO".equals(form.getTipo()) &&
                (form.getDescripcion() == null || form.getDescripcion().isBlank()))
            throw new IllegalArgumentException(
                "Para el tipo 'Otro' debes indicar una descripción o nombre del bloque.");

        TipoRecurrencia tipoRec = parseTipoRecurrencia(form.getTipoRecurrencia());

        BloqueHorario bh = new BloqueHorario();
        bh.setUsuario(usuario);
        bh.setTipo(TipoBloque.valueOf(form.getTipo()));
        bh.setDescripcion(form.getDescripcion());
        bh.setInicio(inicio);
        bh.setFin(fin);
        bh.setTipoRecurrencia(tipoRec);
        bh.setDiasSemana(form.getDiasSemana());
        if (form.getFechaFinRecurrencia() != null && !form.getFechaFinRecurrencia().isBlank()) {
            try { bh.setFechaFinRecurrencia(LocalDate.parse(form.getFechaFinRecurrencia())); }
            catch (Exception ex) { /* fecha inválida: ignorar y usar límite por defecto */ }
        }
        entityManager.persist(bh);
        log.info("Bloque horario {} ({}) creado para usuario {}",
            bh.getTipo(), tipoRec, usuario.getUsername());
    }

    /**
     * Elimina un bloque horario del usuario.
     *
     * @throws IllegalArgumentException si el bloque no existe
     * @throws SecurityException        si el bloque pertenece a otro usuario
     */
    @Transactional
    public void eliminarBloque(long bloqueId, long usuarioId) {
        BloqueHorario bh = entityManager.find(BloqueHorario.class, bloqueId);
        if (bh == null)
            throw new IllegalArgumentException("Bloque no encontrado");
        if (bh.getUsuario().getId() != usuarioId)
            throw new SecurityException("No puedes eliminar bloques de otro usuario");
        entityManager.remove(bh);
        log.info("Bloque {} eliminado por usuario {}", bloqueId, usuarioId);
    }

    // -----------------------------------------------------------------------
    // Utilidades internas
    // -----------------------------------------------------------------------

    /** Parsea un string de recurrencia (null/"" → NINGUNA). */
    private TipoRecurrencia parseTipoRecurrencia(String s) {
        if (s == null || s.isBlank()) return TipoRecurrencia.NINGUNA;
        try { return TipoRecurrencia.valueOf(s); }
        catch (IllegalArgumentException e) { return TipoRecurrencia.NINGUNA; }
    }

    /**
     * Genera la lista de fechas de inicio para una serie recurrente de eventos.
     * La primera fecha es {@code base} y se generan hasta {@value MAX_SERIE_EVENTOS} en total.
     *
     * @param base       inicio del primer evento de la serie
     * @param tipo       tipo de recurrencia
     * @param diasSemana días ISO (1-7) separados por coma, para PERSONALIZADA
     * @return lista de fechas de inicio (incluye {@code base})
     */
    private List<LocalDateTime> generarFechasParaSerie(
            LocalDateTime base, TipoRecurrencia tipo, String diasSemana) {
        List<LocalDateTime> fechas = new ArrayList<>();
        fechas.add(base);

        if (tipo == TipoRecurrencia.NINGUNA) return fechas;

        Set<Integer> dias = parseDiasSemana(diasSemana);
        LocalDateTime cursor = base;
        int safety = MAX_SERIE_EVENTOS * 30; // evita bucle infinito en PERSONALIZADA con pocos días

        while (fechas.size() < MAX_SERIE_EVENTOS && safety-- > 0) {
            LocalDateTime candidato = switch (tipo) {
                case DIARIA        -> cursor.plusDays(1);
                case SEMANAL       -> cursor.plusWeeks(1);
                case MENSUAL       -> cursor.plusMonths(1);
                case PERSONALIZADA -> cursor.plusDays(1);
                default            -> null;
            };
            if (candidato == null) break;
            cursor = candidato;

            if (tipo == TipoRecurrencia.PERSONALIZADA
                    && !dias.isEmpty()
                    && !dias.contains(cursor.getDayOfWeek().getValue())) {
                continue; // día no seleccionado
            }
            fechas.add(cursor);
        }
        return fechas;
    }

    /**
     * Expande un bloque recurrente generando sus ocurrencias dentro del rango {@code [rsdt, redt)}.
     * El número máximo de ocurrencias devueltas está acotado a 400 para evitar feeds enormes.
     */
    private List<LocalDateTime[]> expandirBloqueEnRango(
            BloqueHorario bh, LocalDateTime rsdt, LocalDateTime redt) {
        TipoRecurrencia tipo = bh.getTipoRecurrencia();
        Duration duracion    = Duration.between(bh.getInicio(), bh.getFin());
        Set<Integer> dias    = parseDiasSemana(bh.getDiasSemana());

        LocalDateTime limiteRec = bh.getFechaFinRecurrencia() != null
            ? bh.getFechaFinRecurrencia().plusDays(1).atStartOfDay()
            : redt; // si no hay límite explícito, usar el fin del rango

        List<LocalDateTime[]> result = new ArrayList<>();
        LocalDateTime cursor  = bh.getInicio();
        int safety = 400;

        while (!cursor.isAfter(redt) && !cursor.isAfter(limiteRec) && safety-- > 0) {
            LocalDateTime ocFin = cursor.plus(duracion);

            if (!ocFin.isBefore(rsdt)) { // solapa con el rango visible
                boolean incluir = switch (tipo) {
                    case DIARIA        -> true;
                    case SEMANAL       -> true;
                    case MENSUAL       -> true;
                    case PERSONALIZADA -> dias.isEmpty() || dias.contains(cursor.getDayOfWeek().getValue());
                    default            -> false;
                };
                if (incluir) result.add(new LocalDateTime[]{cursor, ocFin});
            }

            cursor = switch (tipo) {
                case DIARIA        -> cursor.plusDays(1);
                case SEMANAL       -> cursor.plusWeeks(1);
                case MENSUAL       -> cursor.plusMonths(1);
                case PERSONALIZADA -> cursor.plusDays(1);
                default            -> redt.plusDays(1);
            };
        }
        return result;
    }

    /**
     * Construye una entrada del feed FullCalendar para un bloque (simple o una ocurrencia).
     * <p>
     * El color base se asigna por usuario (determinista: {@code userId % PALETA_BLOQUES.length}),
     * no por tipo. El JS de la vista usa la misma paleta para el panel de conflictos y el modal.
     *
     * @param idSuffix sufijo añadido al id para distinguir ocurrencias ("-0", "-1", …)
     */
    private Map<String, Object> construirEntradaBloque(
            BloqueHorario bh, LocalDateTime inicio, LocalDateTime fin,
            boolean esMio, String idSuffix) {

        String tipoNombre = bh.getTipo().name();
        String emoji      = EMOJI_TIPO.getOrDefault(tipoNombre, "📌");
        // Descripción: la real si existe, si no, el emoji+tipo como fallback legible
        String rawDesc    = bh.getDescripcion() != null && !bh.getDescripcion().isBlank()
                            ? bh.getDescripcion() : null;
        String displayDesc = rawDesc != null ? rawDesc : (emoji + " " + tipoNombre.toLowerCase());

        // Color por usuario — mismo índice siempre para la misma persona
        String baseColor = PALETA_BLOQUES[(int)(bh.getUsuario().getId() % PALETA_BLOQUES.length)];
        // Título: nombre de la persona siempre visible, luego emoji y descripción
        String firstName = bh.getUsuario().getFirstName();
        String title = firstName + " · " + emoji + " " + displayDesc;

        TipoRecurrencia tr = bh.getTipoRecurrencia() != null
            ? bh.getTipoRecurrencia() : TipoRecurrencia.NINGUNA;

        Map<String, Object> props = new HashMap<>();
        props.put("tipo",             "BLOQUE");
        props.put("tipoBloque",       tipoNombre);
        props.put("descripcion",      rawDesc != null ? rawDesc : "");
        props.put("propietario",      bh.getUsuario().getUsername());
        props.put("nombrePropietario", bh.getUsuario().getFirstName() + " " + bh.getUsuario().getLastName());
        props.put("propietarioId",    bh.getUsuario().getId());
        props.put("color",            baseColor);
        props.put("esMio",            esMio);
        props.put("bloqueId",         bh.getId());
        if (tr != TipoRecurrencia.NINGUNA) props.put("tipoRecurrencia", tr.name());

        // Bloques propios: color sólido + texto blanco. Ajenos: color translúcido + texto oscuro.
        String bgColor = esMio ? baseColor : baseColor + "55";
        String txColor = esMio ? "#ffffff" : "#000000";

        Map<String, Object> entry = new HashMap<>();
        entry.put("id",              "bloque-" + bh.getId() + idSuffix);
        entry.put("title",           title);
        entry.put("start",           inicio.toString());
        entry.put("end",             fin.toString());
        entry.put("backgroundColor", bgColor);
        entry.put("borderColor",     baseColor);
        entry.put("textColor",       txColor);
        entry.put("extendedProps",   props);
        return entry;
    }

    /** Parsea una cadena "1,3,5" en un Set de enteros ISO de día de semana (1=lunes). */
    private Set<Integer> parseDiasSemana(String diasSemana) {
        Set<Integer> result = new HashSet<>();
        if (diasSemana == null || diasSemana.isBlank()) return result;
        for (String d : diasSemana.split(",")) {
            try { result.add(Integer.parseInt(d.trim())); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    /**
     * Analiza un string datetime-local tolerando el formato con o sin segundos.
     * Los inputs {@code datetime-local} de HTML envían {@code HH:mm} sin segundos,
     * mientras que {@link LocalDateTime#parse(CharSequence)} los requiere.
     */
    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank())
            throw new IllegalArgumentException("Fecha requerida");
        // Normaliza HH:mm → HH:mm:ss
        if (s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$")) s = s + ":00";
        return LocalDateTime.parse(s);
    }

    // -----------------------------------------------------------------------
    // Detección de conflictos
    // -----------------------------------------------------------------------

    /**
     * Detecta conflictos entre un evento y los bloques horarios de los miembros del piso.
     * <p>
     * Un conflicto existe cuando: {@code bloque.inicio < evento.fin AND bloque.fin > evento.inicio}.
     *
     * @param eventoId identificador del evento
     * @param pisoId   identificador del piso (para filtrar solo miembros activos)
     * @return lista de mapas con los datos del conflicto: usuario, tipo, descripcion, inicio, fin
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> detectarConflictos(long eventoId, long pisoId) {
        Evento evento = entityManager.find(Evento.class, eventoId);
        if (evento == null) return List.of();
        if (evento.getPiso().getId() != pisoId)
            throw new SecurityException("El evento no pertenece a este piso");

        List<BloqueHorario> bloques = entityManager.createQuery(
                "SELECT bh FROM BloqueHorario bh " +
                "WHERE bh.usuario IN (" +
                "    SELECT mp.usuario FROM MiembroPiso mp " +
                "    WHERE mp.piso.id = :pisoId AND mp.fechaSalida IS NULL" +
                ") " +
                "AND bh.inicio < :eventoFin " +
                "AND bh.fin > :eventoInicio",
                BloqueHorario.class)
            .setParameter("pisoId", pisoId)
            .setParameter("eventoFin",    evento.getFechaFin())
            .setParameter("eventoInicio", evento.getFechaInicio())
            .getResultList();

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (BloqueHorario bh : bloques) {
            Map<String, Object> m = new HashMap<>();
            m.put("usuario",    bh.getUsuario().getUsername());
            m.put("usuarioId",  bh.getUsuario().getId());
            m.put("nombre",     bh.getUsuario().getFirstName() + " " + bh.getUsuario().getLastName());
            m.put("tipo",       bh.getTipo().name());
            m.put("descripcion", bh.getDescripcion() != null ? bh.getDescripcion() : "");
            m.put("inicio",     bh.getInicio().toString());
            m.put("fin",        bh.getFin().toString());
            resultado.add(m);
        }
        return resultado;
    }
}
