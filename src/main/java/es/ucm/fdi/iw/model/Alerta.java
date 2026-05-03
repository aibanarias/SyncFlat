package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;

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
 * Notificación interna asociada a un piso.
 * <p>
 * Las alertas informan a los miembros de eventos relevantes
 * (p. ej. gasto añadido, tarea vencida). El campo {@code leida} indica
 * si el destinatario ya la ha visto.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "piso_id")
    private Piso piso;

    private String mensaje;
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    private TipoAlerta tipo;

    private boolean leida;
}
