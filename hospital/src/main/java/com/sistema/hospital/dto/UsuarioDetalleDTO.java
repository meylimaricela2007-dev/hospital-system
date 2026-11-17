package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Todos los campos de un usuario, para precargar el formulario de
 * "Editar Usuario" (FA04, paso 2): a diferencia de UsuarioDTO (que solo
 * trae lo que se ve en la tabla), este trae también dpi, nit, teléfono,
 * sucursal y especialidad.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDetalleDTO {
    private Integer idUsuario;
    private String nombreCompleto;
    private String correoElectronico;
    private String nombreUsuario;
    private String dpi;
    private String nit;
    private String telefono;
    private String numeroSeguro;
    private Integer idRol;
    private Integer idSucursal;
    private Integer idEspecialidad;
    private boolean estadoActivo;
}
