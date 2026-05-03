package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Formulario de creación de un nuevo piso. */
@Data
public class CrearPisoForm {

    @NotBlank(message = "El nombre del piso es obligatorio")
    private String nombre;

    @NotBlank(message = "La dirección es obligatoria")
    private String direccion;
}
