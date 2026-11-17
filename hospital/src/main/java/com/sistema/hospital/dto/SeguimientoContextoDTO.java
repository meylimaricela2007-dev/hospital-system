package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CU-12, paso 2: lo que el sistema pre-carga a partir de la consulta padre y
 * su cita: paciente, médico, especialidad y sucursal.
 */
@Data
@AllArgsConstructor
public class SeguimientoContextoDTO {
    private Integer idConsulta;
    private Integer idPaciente;
    private String nombrePaciente;
    private Integer idMedico;
    private String nombreMedico;
    private Integer idEspecialidad;
    private String especialidad;
    private Integer idSucursal;
    private String sucursal;
}
