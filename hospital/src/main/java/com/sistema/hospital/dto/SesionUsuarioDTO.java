package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Lo que se guarda en la sesión HTTP tras un login exitoso (ver AuthService).
 * fragments/header.html lo usa así: ${session.usuario.nombreCompleto}
 * para mostrar el nombre en la barra de navegación.
 */
@Data
@AllArgsConstructor
public class SesionUsuarioDTO {
    private Integer idUsuario;
    private String nombreCompleto;
    private String nombreUsuario;
    private String rolNombre;
}
