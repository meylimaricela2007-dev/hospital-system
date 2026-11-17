package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;

/** CU-07, paso 1: una fila de la cola (columna "Paciente Presente" o "Signos Vitales"). */
@Data
@AllArgsConstructor
public class PacienteColaDTO {
    private Integer idCita;
    private String nombrePaciente;
    private String especialidad;
    private String nombreMedico;
    private LocalTime horaCita;
    private boolean esEmergencia;
    private String estadoCita;
}
