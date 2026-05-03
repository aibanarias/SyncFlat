package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Formulario de alta de un producto en el catálogo del piso. */
@Data
public class ProductoForm {

    @NotBlank(message = "El nombre del producto es obligatorio")
    private String nombre;

    private String categoria;
}
