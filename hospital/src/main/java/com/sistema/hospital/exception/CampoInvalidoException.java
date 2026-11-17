package com.sistema.hospital.exception;

import lombok.Getter;

/**
 * A diferencia de ResponseStatusException (un mensaje general, sin campo),
 * esta excepción sabe A CUÁL campo del formulario pertenece el error —
 * ej. "el nombre de usuario ya está en uso" es un problema del campo
 * "nombreUsuario", no un error genérico. GlobalExceptionHandler la traduce
 * al mismo formato { message, errors: { campo: mensaje } } que ya usan los
 * errores de validación (@Valid), así el frontend puede resaltar el input
 * exacto en rojo en vez de solo mostrar la alerta general de arriba.
 */
@Getter
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }
}
