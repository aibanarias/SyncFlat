package es.ucm.fdi.iw.model;

/**
 * Interfaz para entidades que pueden transformarse en un objeto de transferencia (DTO).
 * Permite serializar a JSON sin exponer campos sensibles como contraseñas.
 */
public interface Transferable<T> {
    T toTransfer();
}
