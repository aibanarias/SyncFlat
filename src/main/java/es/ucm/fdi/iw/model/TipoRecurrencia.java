package es.ucm.fdi.iw.model;

/**
 * Tipo de repetición de un evento o bloque horario.
 * <p>
 * Para eventos recurrentes, al crear la cabeza de serie se generan automáticamente
 * hasta {@code MAX_SERIE_EVENTOS} instancias individuales que siguen el mismo ciclo
 * de estados (PROPUESTO → APROBADO → RECHAZADO) de forma independiente.
 * <p>
 * Para bloques recurrentes, las ocurrencias se generan en el feed dentro del rango
 * de fechas solicitado por FullCalendar.
 */
public enum TipoRecurrencia {
    /** Sin repetición. */
    NINGUNA,
    /** Repite cada día a la misma hora. */
    DIARIA,
    /** Repite cada semana el mismo día de la semana. */
    SEMANAL,
    /** Repite cada mes el mismo día del mes. */
    MENSUAL,
    /** Repite en días concretos de la semana (almacenados en {@code diasSemana}). */
    PERSONALIZADA
}
