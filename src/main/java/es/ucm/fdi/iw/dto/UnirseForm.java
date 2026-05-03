package es.ucm.fdi.iw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Formulario para unirse a un piso mediante código de invitación. */
@Data
public class UnirseForm {

    @NotBlank(message = "El código de invitación es obligatorio")
    private String codigo;
}
