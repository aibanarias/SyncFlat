package es.ucm.fdi.iw.dto;

import java.math.BigDecimal;

import es.ucm.fdi.iw.model.User;

/** Deuda neta de un usuario hacia otro dentro del piso, resultado de agregar y compensar gastos pendientes. */
public record DeudaNeta(User deudor, User acreedor, BigDecimal importe) {}
