package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una fila de la tabla "Listado de Usuarios" (CU-01, paso 7).
 * Columnas exactas de la especificación: ID, Nombre, Correo Electrónico,
 * Rol, Nombre de Usuario, Estado y Acciones (Acciones se dibuja en el
 * frontend usando idUsuario, no es un campo aparte).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {
    private Integer idUsuario;
    private String nombreCompleto;
    private String correoElectronico;
    private String rolNombre;
    private String nombreUsuario;
    private String estadoNombre;
}
