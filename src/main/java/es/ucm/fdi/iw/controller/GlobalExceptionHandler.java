package es.ucm.fdi.iw.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.ucm.fdi.iw.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Manejador global de excepciones de negocio para endpoints JSON ({@code @ResponseBody}).
 *
 * <p>Los controladores MVC que devuelven redirecciones con flash messages capturan
 * sus propias excepciones internamente; este advice solo intercepta las que escapan
 * de endpoints {@code @ResponseBody}, es decir, los endpoints AJAX.
 *
 * <p>Mapa de excepciones → códigos HTTP:
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400 Bad Request</li>
 *   <li>{@link IllegalStateException}    → 400 Bad Request</li>
 *   <li>{@link SecurityException}        → 403 Forbidden</li>
 * </ul>
 *
 * <p>El formato de respuesta es siempre {@code ApiResponse} estándar:
 * {@code { "ok": false, "message": "...", "data": null }}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    /**
     * Petición inválida por argumento incorrecto o estado no permitido.
     * Errores de dominio esperados que el cliente puede corregir.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<?>> handleClientError(
            RuntimeException e, HttpServletRequest req) {
        log.debug("Error de cliente en {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    /**
     * Acceso denegado: el usuario no tiene permisos para la operación solicitada.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<?>> handleSecurityError(
            SecurityException e, HttpServletRequest req) {
        log.warn("Acceso denegado en {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(403).body(ApiResponse.error("no autorizado"));
    }
}
