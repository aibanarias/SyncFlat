package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Formulario para registrar una compra física realizada a partir de una lista.
 * <p>
 * {@code fecha} es opcional: si se omite, el servicio usa la fecha actual.
 * {@code crearGasto} es un checkbox: si está marcado, se genera automáticamente
 * un {@code Gasto} compartido y se reparte de forma equitativa entre los miembros activos.
 */
@Data
public class RegistrarCompraForm {

    @NotNull(message = "Debes seleccionar una lista")
    private Long listaId;

    @NotNull(message = "El importe total es obligatorio")
    @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero")
    private BigDecimal importeTotal;

    /** Fecha de la compra en formato ISO (yyyy-MM-dd). Opcional; por defecto: hoy. */
    private String fecha;

    /** Si es {@code true}, se genera un {@link es.ucm.fdi.iw.model.Gasto} compartido. */
    private boolean crearGasto;
}
