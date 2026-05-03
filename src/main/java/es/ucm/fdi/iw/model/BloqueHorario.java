package es.ucm.fdi.iw.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Franja horaria personal de un usuario.
 * <p>
 * Permite al usuario declarar periodos en los que estará ocupado.
 * El sistema los usa para detectar conflictos con eventos del piso.
 * <p>
 * Se usa {@code @Getter}/{@code @Setter} en lugar de {@code @Data} para evitar
 * que Lombok genere {@code equals()}/{@code hashCode()} incluyendo la relación
 * {@code @ManyToOne usuario}, lo que podría provocar carga innecesaria.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class BloqueHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoBloque tipo;

    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    /** Tipo de repetición. {@code null} equivale a {@link TipoRecurrencia#NINGUNA}. */
    @Enumerated(EnumType.STRING)
    private TipoRecurrencia tipoRecurrencia;

    /** Días de la semana para {@link TipoRecurrencia#PERSONALIZADA} (ej: {@code "1,3,5"}). */
    private String diasSemana;

    /** Fecha hasta la que se generan ocurrencias. {@code null} = sin límite (hasta 1 año). */
    private java.time.LocalDate fechaFinRecurrencia;
}
