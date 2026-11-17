package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** CU-08, FA01: "El médico selecciona exámenes requeridos y agrega observaciones." */
@Data
public class GenerarOrdenLaboratorioRequest {

    @NotEmpty(message = "Debe seleccionar al menos un examen.")
    private List<Integer> idsExamenes;

    private String observaciones;

    // NUEVO (CU-09, FA01): el médico marca la casilla "Orden Externa" si el
    // paciente se hará los exámenes en un laboratorio externo. Esa orden no
    // se cobra en Caja ni se procesa en el laboratorio interno.
    private Boolean esExterna;
}
