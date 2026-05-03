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
 * Producto del catálogo compartido de un piso.
 * <p>
 * El catálogo sirve como base para añadir ítems a las {@link ListaCompra}.
 * La categoría agrupa productos para facilitar la organización.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String nombre;
    private String categoria;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;
}
