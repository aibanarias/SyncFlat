package es.ucm.fdi.iw.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lista de la compra de un piso.
 * <p>
 * Contiene un conjunto de {@link ItemListaCompra} con los productos a adquirir.
 * El campo {@code completada} se pone a {@code true} cuando todos los ítems
 * han sido comprados o la lista se cierra manualmente.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ListaCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String nombre;
    private LocalDate fechaCreacion;
    private boolean completada;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;

    /**
     * Miembros del piso que participan en esta lista.
     * <p>
     * Si está vacía la lista se considera <em>pública de piso</em>: el reparto económico
     * aplica a todos los miembros activos en el momento del cierre.
     * <p>
     * Se usa {@code EAGER} porque los participantes se acceden en el 100 % de los
     * contextos de renderizado (listas activas, gestión e historial), siempre fuera
     * de la sesión de persistencia. La cardinalidad está acotada al número de miembros
     * del piso (típicamente 2–6), por lo que el sobrecoste es despreciable.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "Lista_Participante",
        joinColumns = @JoinColumn(name = "lista_id"),
        inverseJoinColumns = @JoinColumn(name = "usuario_id")
    )
    private List<User> participantes = new ArrayList<>();
}
