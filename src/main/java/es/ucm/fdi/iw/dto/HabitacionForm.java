package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Formulario para que un miembro asigne su número de habitación. */
@Data
public class HabitacionForm {

    @NotNull(message = "Debes indicar un número de habitación")
    @Min(value = 1, message = "El número de habitación debe ser al menos 1")
    private Integer numHabitacion;
}
