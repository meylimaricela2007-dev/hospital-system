package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** CU-13, RN-CU12-01: Sede y Especialidad son obligatorias. */
@Data
public class AsignarSedeEspecialidadRequest {

    @NotNull(message = "Debe seleccionar una sede.")
    private Integer idSucursal;

    @NotNull(message = "Debe seleccionar una especialidad.")
    private Integer idEspecialidad;
}
