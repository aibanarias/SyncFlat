package es.ucm.fdi.iw.model;

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
 * Ítem de una lista de la compra.
 * <p>
 * Relaciona un {@link Producto} con una {@link ListaCompra} indicando la
 * cantidad solicitada y si ya ha sido comprado. El campo {@code solicitadoPor}
 * identifica al miembro que añadió el ítem.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ItemListaCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "lista_id")
    private ListaCompra lista;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    private int cantidad;
    private boolean comprado;

    @ManyToOne
    @JoinColumn(name = "solicitado_por_id")
    private User solicitadoPor;
}
