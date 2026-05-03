package es.ucm.fdi.iw.model;

import java.math.BigDecimal;
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
 * Registro de una compra efectuada a partir de una lista de la compra.
 * <p>
 * Vincula la {@link ListaCompra} completada con el {@link Gasto} generado
 * e identifica al miembro que realizó la compra física.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private LocalDate fecha;
    private BigDecimal importeTotal;

    @ManyToOne
    @JoinColumn(name = "comprador_id")
    private User comprador;

    @ManyToOne
    @JoinColumn(name = "lista_id")
    private ListaCompra lista;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;

    @ManyToOne
    @JoinColumn(name = "gasto_id")
    private Gasto gasto;
}
