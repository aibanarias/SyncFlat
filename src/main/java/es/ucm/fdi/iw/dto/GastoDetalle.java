package es.ucm.fdi.iw.dto;

import java.math.BigDecimal;
import java.util.List;

import es.ucm.fdi.iw.model.Gasto;
import es.ucm.fdi.iw.model.ParticipanteGasto;

/**
 * Agrupación de un {@link Gasto} con sus participaciones para la vista del historial.
 * Calculado por {@link es.ucm.fdi.iw.service.GastoService#obtenerHistorial}.
 */
public record GastoDetalle(Gasto gasto, List<ParticipanteGasto> participantes) {

    /**
     * Suma de importes pendientes de pago en este gasto.
     * <p>
     * Excluye la participación del pagador: el pagador desembolsó el total
     * y su participación queda implícitamente saldada. Contar su cuota como
     * deuda pendiente genera una discrepancia con el balance neto.
     */
    public BigDecimal importePendiente() {
        long pagadorId = gasto.getPagador().getId();
        return participantes.stream()
            .filter(pg -> !pg.isPagado() && pg.getUsuario().getId() != pagadorId)
            .map(ParticipanteGasto::getImporteAsignado)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
