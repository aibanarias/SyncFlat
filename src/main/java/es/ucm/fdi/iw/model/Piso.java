package es.ucm.fdi.iw.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unidad habitacional que agrupa a varios usuarios (miembros).
 * Es la entidad raíz del dominio: gastos, tareas, eventos y listas de compra
 * pertenecen siempre a un piso concreto.
 * <p>
 * El {@code codigoInvitacion} es un código único alfanumérico de 6 caracteres
 * generado al crear el piso; los usuarios lo usan para unirse al piso.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Piso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String nombre;
    private String direccion;
    private LocalDate fechaCreacion;

    @Column(nullable = false, unique = true, length = 10)
    private String codigoInvitacion;

    /** Número total de habitaciones del piso. {@code null} si no ha sido configurado aún. */
    private Integer numHabitaciones;
}
