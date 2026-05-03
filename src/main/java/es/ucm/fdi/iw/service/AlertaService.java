package es.ucm.fdi.iw.service;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.ucm.fdi.iw.dto.AlertaWsPayload;
import es.ucm.fdi.iw.model.Alerta;
import es.ucm.fdi.iw.model.MiembroPiso;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.TipoAlerta;
import jakarta.persistence.EntityManager;

/**
 * Servicio centralizado de alertas del piso.
 *
 * <p>Todos los módulos que necesitan notificar a los miembros deben llamar a
 * {@link #crearAlerta} en lugar de manipular la entidad {@link Alerta} directamente.
 * Esto garantiza uniformidad en el formato, el tipo y la fecha.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Solo el servicio crea alertas; los controladores nunca lo hacen directamente.</li>
 *   <li>Marcar como leída requiere que la alerta pertenezca al piso del solicitante.</li>
 *   <li>Las alertas son a nivel de piso: todos los miembros las ven.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Service
public class AlertaService {

    private static final Logger log = LogManager.getLogger(AlertaService.class);

    private final EntityManager entityManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Lectura
    // -------------------------------------------------------------------------

    /** Devuelve las alertas no leídas del piso, ordenadas por fecha descendente. */
    @Transactional(readOnly = true)
    public List<Alerta> obtenerNoLeidas(long pisoId) {
        return entityManager
            .createQuery(
                "SELECT a FROM Alerta a WHERE a.piso.id = :pid AND a.leida = false ORDER BY a.fecha DESC",
                Alerta.class)
            .setParameter("pid", pisoId)
            .getResultList();
    }

    // -------------------------------------------------------------------------
    // Escritura
    // -------------------------------------------------------------------------

    /**
     * Crea y persiste una alerta de piso.
     *
     * <p>Se llama desde dentro de los métodos {@code @Transactional} de otros servicios;
     * participa en la transacción externa (propagación REQUIRED por defecto).
     *
     * @param mensaje texto de la notificación
     * @param tipo    categoría de urgencia
     * @param piso    piso al que se asocia la alerta
     * @return la {@link Alerta} persistida
     */
    @Transactional
    public Alerta crearAlerta(String mensaje, TipoAlerta tipo, Piso piso) {
        Alerta a = new Alerta();
        a.setMensaje(mensaje);
        a.setTipo(tipo);
        a.setFecha(LocalDateTime.now());
        a.setLeida(false);
        a.setPiso(piso);
        entityManager.persist(a);
        entityManager.flush(); // garantiza que a.getId() tiene valor (IDENTITY strategy)
        emitirPorWebSocket(a, piso);
        log.debug("Alerta [{}] creada en piso {}: {}", tipo, piso.getNombre(), mensaje);
        return a;
    }

    private void emitirPorWebSocket(Alerta a, Piso piso) {
        try {
            String json = objectMapper.writeValueAsString(
                new AlertaWsPayload("ALERTA", a.getId(), a.getMensaje(), a.getTipo().name()));
            entityManager.createQuery(
                "SELECT mp FROM MiembroPiso mp WHERE mp.piso.id = :pid AND mp.fechaSalida IS NULL",
                MiembroPiso.class)
                .setParameter("pid", piso.getId())
                .getResultList()
                .forEach(mp -> messagingTemplate.convertAndSend(
                    "/user/" + mp.getUsuario().getUsername() + "/queue/updates", json));
        } catch (JsonProcessingException e) {
            log.warn("No se pudo emitir alerta por WebSocket (piso {}): {}", piso.getId(), e.getMessage());
        }
    }

    /**
     * Marca una alerta concreta como leída.
     *
     * @param alertaId identificador de la alerta
     * @param pisoId   piso del solicitante (guard de seguridad)
     * @throws IllegalArgumentException si la alerta no existe o no pertenece al piso
     * @throws IllegalStateException    si la alerta ya estaba leída
     */
    @Transactional
    public void marcarLeida(long alertaId, long pisoId) {
        Alerta a = entityManager.find(Alerta.class, alertaId);
        if (a == null || a.getPiso().getId() != pisoId)
            throw new IllegalArgumentException("Alerta no encontrada.");
        if (a.isLeida())
            throw new IllegalStateException("La alerta ya estaba leída.");
        a.setLeida(true);
        log.debug("Alerta {} marcada como leída", alertaId);
    }

    /**
     * Marca como leídas todas las alertas pendientes del piso.
     *
     * @param pisoId piso del solicitante
     * @return número de alertas actualizadas
     */
    @Transactional
    public int marcarTodasLeidas(long pisoId) {
        int n = entityManager
            .createQuery("UPDATE Alerta a SET a.leida = true WHERE a.piso.id = :pid AND a.leida = false")
            .setParameter("pid", pisoId)
            .executeUpdate();
        log.info("{} alerta(s) marcadas como leídas en piso {}", n, pisoId);
        return n;
    }
}
