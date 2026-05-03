package es.ucm.fdi.iw.model;

/**
 * Estado de aprobación colectiva de un evento del piso.
 * <p>
 * Un evento arranca siempre como {@code PROPUESTO}; cualquier miembro puede
 * aprobarlo o rechazarlo. Un evento rechazado puede reabrirse como propuesta.
 * La asistencia individual ({@link AsistenciaEvento}) solo tiene sentido sobre
 * eventos en estado {@code APROBADO}.
 * <p>
 * Flujo permitido:
 * <pre>
 *   PROPUESTO ──aprobar──► APROBADO
 *   PROPUESTO ──rechazar─► RECHAZADO ──reabrir──► PROPUESTO
 *   APROBADO  ──rechazar─► RECHAZADO
 * </pre>
 */
public enum EstadoEvento {
    PROPUESTO,
    APROBADO,
    RECHAZADO
}
