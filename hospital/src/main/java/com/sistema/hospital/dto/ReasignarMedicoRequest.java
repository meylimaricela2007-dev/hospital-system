package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** CU-05, FA07 "Reasignación de médico". */
@Data
public class ReasignarMedicoRequest {

    @NotNull(message = "Debe seleccionar el nuevo médico.")
    private Integer idMedicoNuevo;

    // Opcional según el documento ("Opcionalmente se puede agregar una
    // nota con el motivo de la reasignación"). Se guarda en
    // "reasignaciones_medico" (columna "nota"), la bitácora de auditoría.
    private String notaMotivo;
}
