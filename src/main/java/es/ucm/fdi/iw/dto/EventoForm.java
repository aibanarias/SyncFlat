package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Formulario de creación de un evento del calendario. */
@Data
public class EventoForm {

    @NotBlank(message = "El título es obligatorio")
    private String titulo;

    private String descripcion;

    @NotBlank(message = "La fecha de inicio es obligatoria")
    private String fechaInicio;

    @NotBlank(message = "La fecha de fin es obligatoria")
    private String fechaFin;

    @Pattern(regexp = "^(NINGUNA|DIARIA|SEMANAL|MENSUAL|PERSONALIZADA)?$",
             message = "Tipo de recurrencia no válido")
    private String tipoRecurrencia = "NINGUNA";

    /** Días ISO separados por coma para PERSONALIZADA (ej: "1,3,5"). Puede ser nulo. */
    private String diasSemana;
}
