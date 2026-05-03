package es.ucm.fdi.iw.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tarea doméstica definida en el contexto de un piso.
 * <p>
 * Una tarea puede ser puntual o periódica (campo {@code frecuencia}) y se
 * asigna a uno o varios miembros mediante {@link AsignacionTarea}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String nombre;
    private String descripcion;

    @Enumerated(EnumType.STRING)
    private TipoTarea tipo;

    @Enumerated(EnumType.STRING)
    private FrecuenciaTarea frecuencia;
    private LocalDate fechaLimite;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;
}
