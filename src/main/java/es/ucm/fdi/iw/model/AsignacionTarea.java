package es.ucm.fdi.iw.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Asignación de una tarea a un usuario concreto.
 * <p>
 * {@code fechaCompletada} nula indica que la tarea está pendiente; cuando se
 * rellena, la tarea se considera completada. El campo {@code validada} permite
 * que otro miembro del piso confirme la realización.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AsignacionTarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "tarea_id")
    private Tarea tarea;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private LocalDate fechaAsignacion;
    private LocalDate fechaCompletada;
    private boolean validada;

    @ManyToOne
    @JoinColumn(name = "validador_id")
    private User validador;
}
