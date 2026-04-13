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

/**
 * Relación entre un usuario y un piso.
 * <p>
 * Almacena el rol del usuario dentro del piso ({@link RolPiso}), la fecha de ingreso
 * y, opcionalmente, la fecha de salida. La ausencia de fecha de salida indica
 * que el usuario sigue activo en el piso.
 */
@Entity
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

    public MiembroPiso() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Piso getPiso() {
        return piso;
    }

    public void setPiso(Piso piso) {
        this.piso = piso;
    }

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public RolPiso getRolEnPiso() {
        return rolEnPiso;
    }

    public void setRolEnPiso(RolPiso rolEnPiso) {
        this.rolEnPiso = rolEnPiso;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public LocalDate getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(LocalDate fechaSalida) {
        this.fechaSalida = fechaSalida;
    }
}
