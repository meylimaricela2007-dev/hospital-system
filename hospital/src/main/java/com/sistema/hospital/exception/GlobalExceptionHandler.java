package com.sistema.hospital.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sin esta clase, cuando un @Valid falla (por ejemplo, un campo obligatorio
 * vacío o con formato incorrecto), Spring Boot devuelve su mensaje técnico
 * genérico por defecto: "Validation failed for object='...'. Error count: N".
 * Con @RestControllerAdvice, este método intercepta ESE error específico
 * en cualquier controlador del proyecto (login, registro, usuarios, citas,
 * pago...) y arma un mensaje en español con las reglas que sí fallaron,
 * usando el mismo campo "message" que ya leen todos los archivos .js
 * (fetch().then(...).catch(error => error.message)).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(MethodArgumentNotValidException ex) {
        // Junta los mensajes de TODOS los campos que fallaron (nombre, DPI, contraseña, etc.),
        // no solo el primero, para que el usuario los corrija todos de una vez.
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(" "));

        if (mensaje.isBlank()) {
            mensaje = "Los datos ingresados no son válidos. Revise el formulario.";
        }

        // CORREGIDO: antes solo mandábamos "message" con todo junto, así que
        // el frontend no tenía forma de saber CUÁL campo falló para resaltarlo
        // en rojo (usuario-formulario.js, agendar.html, registro.js, etc. ya
        // tenían los <div class="invalid-feedback" data-error-for="..."> en el
        // HTML, pero nunca se llenaban). Ahora agregamos "errors": un mapa
        // { nombreDelCampo: mensaje }, usando exactamente el mismo nombre de
        // campo del DTO (que en los formularios coincide con el id del input,
        // ej. "contrasena" -> <input id="contrasena">), para que el frontend
        // pueda ubicar el input exacto sin tener que adivinar por texto.
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                errores.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));

        return construirRespuesta(HttpStatus.BAD_REQUEST, mensaje, errores);
    }

    /**
     * Los errores de negocio (usuario duplicado, cita no encontrada, etc.) ya
     * traen un mensaje en español porque los servicios los lanzan así:
     *   throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe...")
     * Este manejador solo se asegura de que el formato de la respuesta sea
     * siempre el mismo (con "message"), sin importar cuál excepción ocurrió.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorDeNegocio(ResponseStatusException ex) {
        return construirRespuesta(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), null);
    }

    /**
     * NUEVO: para las reglas de negocio que SÍ pertenecen a un campo concreto
     * (ej. "el nombre de usuario ya está en uso"), UsuarioService las lanza
     * como CampoInvalidoException en vez de ResponseStatusException, y así
     * también llegan resaltadas en rojo al campo correcto en vez de solo
     * aparecer en la alerta general de arriba del formulario.
     */
    @ExceptionHandler(CampoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> manejarCampoInvalido(CampoInvalidoException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        errores.put(ex.getCampo(), ex.getMessage());
        return construirRespuesta(HttpStatus.CONFLICT, ex.getMessage(), errores);
    }

    /**
     * QA: sin estos manejadores, un cuerpo JSON mal formado, una violación de restricción de la
     * BD o un choque de bloqueo optimista devolvían la página HTML de error de Spring, y el
     * JavaScript (que siempre hace r.json()) mostraba un error de conexión confuso.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> manejarBloqueoOptimista(ObjectOptimisticLockingFailureException ex) {
        return construirRespuesta(HttpStatus.CONFLICT,
                "Operación no permitida. Otro usuario modificó este registro al mismo tiempo. Actualice la pantalla e intente de nuevo.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarIntegridad(DataIntegrityViolationException ex) {
        return construirRespuesta(HttpStatus.CONFLICT,
                "No se pudo guardar la información porque no cumple las reglas de la base de datos (dato duplicado, vacío o fuera de rango). Revise los datos ingresados.", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        return construirRespuesta(HttpStatus.BAD_REQUEST,
                "Los datos enviados no tienen un formato válido. Revise el formulario.", null);
    }

    private ResponseEntity<Map<String, Object>> construirRespuesta(HttpStatus status, String mensaje, Map<String, String> errores) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", status.value());
        cuerpo.put("message", mensaje);
        if (errores != null && !errores.isEmpty()) {
            cuerpo.put("errors", errores);
        }
        return ResponseEntity.status(status).body(cuerpo);
    }

    @ExceptionHandler(CamposInvalidosException.class)
    public ResponseEntity<Map<String, Object>> manejarCamposInvalidos(CamposInvalidosException ex) {
        String mensaje = String.join(" ", ex.getErrores().values());
        return construirRespuesta(HttpStatus.BAD_REQUEST, mensaje, ex.getErrores());
    }
}
