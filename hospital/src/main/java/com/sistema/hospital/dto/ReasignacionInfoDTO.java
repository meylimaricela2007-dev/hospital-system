package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * CU-05, FA07: "se muestra un resumen de la cita (paciente, fecha,
 * especialidad, sede, médico actual) y una lista de médicos disponibles de
 * la misma sede y especialidad".
 */
@Data
@AllArgsConstructor
public class ReasignacionInfoDTO {
    private RecepcionCitaDTO resumenCita;
    private List<MedicoDTO> medicosDisponibles; // ya excluye al médico actual
}
