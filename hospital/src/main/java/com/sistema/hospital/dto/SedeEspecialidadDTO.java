package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Una fila del listado de CU-13: ID, Sede, Especialidad, Estado. */
@Data
@AllArgsConstructor
public class SedeEspecialidadDTO {
    private Integer idSedeEspecialidad;
    private Integer idSucursal;
    private String sedeNombre;
    private Integer idEspecialidad;
    private String especialidadNombre;
    private Boolean estado;
    private String estadoNombre;
}
