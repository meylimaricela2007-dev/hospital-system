package com.sistema.hospital.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** CU-08, FA04: "El médico agrega medicamentos con dosis, frecuencia, duración e indicaciones." */
@Data
public class GenerarRecetaRequest {

    @NotEmpty(message = "Debe agregar al menos un medicamento.")
    @Valid
    private List<DetalleRecetaRequest> medicamentos;

    private String notas;
}
