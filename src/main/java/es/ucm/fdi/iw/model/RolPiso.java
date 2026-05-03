package es.ucm.fdi.iw.model;

/**
 * Rol de un usuario dentro de un piso.
 * <p>
 * {@code ADMIN} puede configurar el piso y promover a otros miembros.
 * {@code MIEMBRO} solo puede gestionar su propia información (habitación, etc.).
 * Puede haber varios ADMINs simultáneos; si el último ADMIN abandona el piso
 * y quedan miembros activos, el sistema reasigna automáticamente a otro miembro.
 */
public enum RolPiso {
    ADMIN,
    MIEMBRO
}
