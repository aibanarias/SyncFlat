package es.ucm.fdi.iw.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Formulario de creación de una lista de la compra. */
@Data
public class ListaForm {

    @NotBlank(message = "El nombre de la lista es obligatorio")
    private String nombre;

    /**
     * IDs de los miembros elegidos para esta lista.
     * <p>
     * {@code null} o vacío → lista <em>pública de piso</em>: participan todos los miembros
     * activos en el momento de cerrar la compra. El reparto económico del gasto sigue
     * la misma regla: todos los miembros activos a partes iguales.
     */
    private List<Long> participanteIds;
}
