package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * CU-05, paso 4 del flujo normal: "El sistema muestra los datos de la cita:
 * nombre del paciente, estado, prioridad (si aplica), número de cita,
 * especialidad, sucursal, fecha, motivo de consulta y hora de llegada (si
 * fue registrada)". Este DTO trae exactamente esos campos, ni más ni menos,
 * más los ids que necesita el frontend para las acciones (registrar
 * llegada, reasignar médico).
 */
@Data
@AllArgsConstructor
public class RecepcionCitaDTO {
    private Integer idCita;            // "número de cita" en el documento
    private Integer idPaciente;
    private String nombrePaciente;
    private Integer idMedico;
    private String nombreMedico;
    private String especialidad;
    private String sucursal;
    private LocalDate fechaCita;
    private LocalTime horaCita;
    private String motivoConsulta;
    private String estadoCita;
    private boolean esEmergencia;      // "prioridad (si aplica)" -> FA08
    private LocalDateTime horaLlegada; // null si todavía no se registra (FA09 / paso 8)
    private String prioridad;   // Baja / Normal / Alta si la cita la trae (seguimiento); null si no aplica
}
