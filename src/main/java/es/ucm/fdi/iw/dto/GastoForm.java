package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Formulario de creación de un gasto compartido. */
@Data
public class GastoForm {

    @NotBlank(message = "El concepto es obligatorio")
    private String concepto;

    @NotNull(message = "El importe es obligatorio")
    @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero")
    private BigDecimal importe;
}
