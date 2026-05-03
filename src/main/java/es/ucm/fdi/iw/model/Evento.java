package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
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
 * Evento del calendario compartido de un piso.
 * <p>
 * El campo {@code estado} refleja la aprobación colectiva del evento
 * ({@link EstadoEvento}). La asistencia individual de cada miembro se gestiona
 * mediante {@link AsistenciaEvento} y solo tiene sentido en eventos {@code APROBADO}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String titulo;
    private String descripcion;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEvento estado = EstadoEvento.PROPUESTO;

    /** Tipo de repetición. {@code null} equivale a {@link TipoRecurrencia#NINGUNA}. */
    @Enumerated(EnumType.STRING)
    private TipoRecurrencia tipoRecurrencia;

    /**
     * Días de la semana para {@link TipoRecurrencia#PERSONALIZADA}.
     * Formato: enteros ISO separados por coma (1=lunes … 7=domingo), ej: {@code "1,3,5"}.
     */
    private String diasSemana;

    /**
     * ID del primer evento de la serie. {@code null} en eventos no recurrentes.
     * En la cabeza de serie apunta a sí mismo; en las ocurrencias apunta al id del primero.
     */
    private Long serieId;

    @ManyToOne
    @JoinColumn(name = "creador_id")
    private User creador;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;
}
