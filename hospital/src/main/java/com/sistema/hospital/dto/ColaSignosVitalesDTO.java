package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * CU-07, paso 1: "El sistema muestra automáticamente la lista de pacientes
 * en estado 'Paciente Presente', separados de los que ya están en proceso
 * de toma de signos."
 */
@Data
@AllArgsConstructor
public class ColaSignosVitalesDTO {
    private List<PacienteColaDTO> enEspera;   // "Paciente Presente"
    private List<PacienteColaDTO> enProceso;  // "Signos Vitales"
}
