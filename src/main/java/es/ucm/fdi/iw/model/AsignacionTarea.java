package es.ucm.fdi.iw.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Asignación de una tarea a un usuario concreto.
 * <p>
 * {@code fechaCompletada} nula indica que la tarea está pendiente; cuando se
 * rellena, la tarea se considera completada. El campo {@code validada} permite
 * que otro miembro del piso confirme la realización.
 */
@Entity
public class AsignacionTarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "tarea_id")
    private Tarea tarea;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private LocalDate fechaAsignacion;
    private LocalDate fechaCompletada;
    private boolean validada;

    @ManyToOne
    @JoinColumn(name = "validador_id")
    private User validador;

    public AsignacionTarea() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Tarea getTarea() {
        return tarea;
    }

    public void setTarea(Tarea tarea) {
        this.tarea = tarea;
    }

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public LocalDate getFechaAsignacion() {
        return fechaAsignacion;
    }

    public void setFechaAsignacion(LocalDate fechaAsignacion) {
        this.fechaAsignacion = fechaAsignacion;
    }

    public LocalDate getFechaCompletada() {
        return fechaCompletada;
    }

    public void setFechaCompletada(LocalDate fechaCompletada) {
        this.fechaCompletada = fechaCompletada;
    }

    public boolean isValidada() {
        return validada;
    }

    public void setValidada(boolean validada) {
        this.validada = validada;
    }

    public User getValidador() {
        return validador;
    }

    public void setValidador(User validador) {
        this.validador = validador;
    }
}
