package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Formulario para añadir un item a una lista de la compra. */
@Data
public class ItemForm {

    @NotNull(message = "Debes seleccionar una lista")
    @Positive
    private Long listaId;

    @NotNull(message = "Debes seleccionar un producto")
    @Positive
    private Long productoId;

    @Min(value = 1, message = "La cantidad mínima es 1")
    private int cantidad = 1;
}
