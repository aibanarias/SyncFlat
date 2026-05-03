package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Formulario para configurar el número total de habitaciones del piso. */
@Data
public class ConfigurarHabitacionesForm {

    @NotNull(message = "Debes indicar el número de habitaciones")
    @Min(value = 1, message = "El número de habitaciones debe ser al menos 1")
    private Integer numHabitaciones;
}
