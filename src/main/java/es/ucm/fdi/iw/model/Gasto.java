package es.ucm.fdi.iw.model;

import java.math.BigDecimal;
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
 * Gasto compartido registrado en el piso.
 * <p>
 * El pagador es quien ha desembolsado el importe; los participantes
 * ({@link ParticipanteGasto}) registran cuánto debe aportar cada miembro.
 * El estado del gasto ({@link EstadoGasto}) refleja si está pendiente de
 * liquidación o ya ha sido saldado.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String concepto;
    private BigDecimal importe;
    private LocalDate fecha;

    @ManyToOne
    @JoinColumn(name = "pagador_id")
    private User pagador;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;

    @Enumerated(EnumType.STRING)
    private EstadoGasto estado;
}
