package es.ucm.fdi.iw.dto;

/**
 * Payload emitido por WebSocket cuando se crea una alerta de piso.
 * <p>
 * El campo {@code tipo = "ALERTA"} permite al frontend distinguir este mensaje
 * de los {@code Message.Transfer} del sistema de mensajería directa,
 * que nunca incluyen ese campo.
 */
public record AlertaWsPayload(
    String tipo,        // siempre "ALERTA"
    long id,
    String mensaje,
    String tipoAlerta   // INFO | URGENTE | RECORDATORIO
) {}
