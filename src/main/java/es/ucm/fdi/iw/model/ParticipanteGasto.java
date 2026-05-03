package es.ucm.fdi.iw.model;

import java.math.BigDecimal;

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
 * Participación de un usuario en un gasto compartido.
 * <p>
 * Registra el importe que corresponde abonar al usuario y si ya lo ha pagado.
 * El pagador original del gasto queda marcado con {@code pagado = true} en
 * el momento de crear los participantes.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ParticipanteGasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "gasto_id")
    private Gasto gasto;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private BigDecimal importeAsignado;
    private boolean pagado;
}
