package es.ucm.fdi.iw.dto;

import java.util.List;

import es.ucm.fdi.iw.model.Compra;
import es.ucm.fdi.iw.model.ItemListaCompra;

/**
 * Agrupación de una {@link Compra} con los ítems marcados que formaron parte de ella.
 * Calculado por {@link es.ucm.fdi.iw.service.CompraService#obtenerHistorialCompras}.
 */
public record CompraDetalle(Compra compra, List<ItemListaCompra> itemsComprados) {}
