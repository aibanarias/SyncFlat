package es.ucm.fdi.iw.dto;

/**
 * Envoltorio estándar para respuestas AJAX.
 * <p>
 * Todos los endpoints que devuelven JSON deben usar este tipo.
 * En caso de error, {@code ok} es {@code false} y {@code message} describe el motivo.
 * En caso de éxito, {@code ok} es {@code true} y {@code data} contiene el resultado.
 */
public record ApiResponse<T>(boolean ok, String message, T data) {

    /** Respuesta de éxito con datos. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data);
    }

    /** Respuesta de error con mensaje descriptivo. */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
