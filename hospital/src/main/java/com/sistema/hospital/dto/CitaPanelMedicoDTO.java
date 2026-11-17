package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * CU-08, paso 1: "Cada tarjeta muestra: número de cita, nombre del
 * paciente, especialidad, fecha y estado. Si el paciente tiene prioridad
 * de emergencia, se muestra la indicación 'Emergencia'."
 */
@Data
@AllArgsConstructor
public class CitaPanelMedicoDTO {
    private Integer idCita;
    private Integer idPaciente;
    private String nombrePaciente;
    private String especialidad;
    private LocalDate fechaCita;
    private LocalTime horaCita;
    private String estadoCita;
    private boolean esEmergencia;
    // Solo viene lleno en la sección "Evaluados" (paso 10): lo necesita el
    // botón "Agendar Seguimiento" (FA02) para enlazar la nueva cita con la
    // consulta que la originó.
    private Integer idConsulta;
}
