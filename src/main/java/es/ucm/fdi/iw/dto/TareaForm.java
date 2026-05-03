package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Formulario de creación de una tarea. */
@Data
public class TareaForm {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;

    @NotBlank(message = "El tipo es obligatorio")
    @Pattern(regexp = "PUNTUAL|RECURRENTE", message = "Tipo de tarea no válido")
    private String tipo;

    @Pattern(regexp = "^(DIARIA|SEMANAL|QUINCENAL|MENSUAL)?$", message = "Frecuencia no válida")
    private String frecuencia;

    @NotBlank(message = "La fecha límite es obligatoria")
    private String fechaLimite;

    private Long asignadoId;
}
