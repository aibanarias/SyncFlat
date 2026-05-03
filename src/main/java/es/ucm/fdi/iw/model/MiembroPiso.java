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
 * Relación entre un usuario y un piso.
 * <p>
 * Almacena el rol del usuario dentro del piso ({@link RolPiso}), la fecha de ingreso
 * y, opcionalmente, la fecha de salida. La ausencia de fecha de salida indica
 * que el usuario sigue activo en el piso.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MiembroPiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Enumerated(EnumType.STRING)
    private RolPiso rolEnPiso;

    private LocalDate fechaIngreso;
    private LocalDate fechaSalida;

    /**
     * Número de habitación ocupada por este miembro en el piso.
     * {@code null} significa que aún no se ha asignado habitación.
     * El valor se conserva en el histórico aunque el miembro abandone el piso.
     */
    private Integer numHabitacion;
}
