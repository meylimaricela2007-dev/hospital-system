package com.sistema.hospital.exception;

import lombok.Getter;
import java.util.Map;

/** Lleva TODOS los errores del formulario juntos: { campo: mensaje }. */
@Getter
public class CamposInvalidosException extends RuntimeException {
    private final Map<String, String> errores;

    public CamposInvalidosException(Map<String, String> errores) {
        super("Hay campos inválidos en el formulario.");
        this.errores = errores;
    }
}