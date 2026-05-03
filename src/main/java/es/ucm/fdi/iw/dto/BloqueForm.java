package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Formulario de creación de un bloque horario personal. */
@Data
public class BloqueForm {

    @NotBlank(message = "El tipo es obligatorio")
    @Pattern(regexp = "TRABAJO|CLASES|ENTRENAMIENTO|OTRO", message = "Tipo de bloque no válido")
    private String tipo;

    private String descripcion;

    @NotBlank(message = "La hora de inicio es obligatoria")
    private String inicio;

    @NotBlank(message = "La hora de fin es obligatoria")
    private String fin;

    @Pattern(regexp = "^(NINGUNA|DIARIA|SEMANAL|MENSUAL|PERSONALIZADA)?$",
             message = "Tipo de recurrencia no válido")
    private String tipoRecurrencia = "NINGUNA";

    /** Días ISO separados por coma para PERSONALIZADA (ej: "1,3,5"). Puede ser nulo. */
    private String diasSemana;

    /** Fecha límite de la serie (YYYY-MM-DD). Nulo = sin límite explícito. */
    private String fechaFinRecurrencia;
}
