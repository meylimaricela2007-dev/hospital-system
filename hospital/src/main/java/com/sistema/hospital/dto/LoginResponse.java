package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Respuesta de POST /api/auth/login, en el formato que ya espera login.html. */
@Data
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private String rol;             // para que el frontend decida a dónde redirigir
    private String mensaje;
    private Integer intentosFallidos; // para mostrar "intentos restantes" en pantalla
}
