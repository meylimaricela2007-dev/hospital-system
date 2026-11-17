package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CU-08, paso 3: "El sistema abre el formulario de consulta médica con el
 * contexto precargado (número de cita y nombre del paciente)". Si ya
 * existía una consulta guardada como "En curso" para esta cita, sus
 * campos vienen prellenados; si es la primera vez, vienen todos en null.
 */
@Data
@AllArgsConstructor
public class ContextoConsultaDTO {
    private Integer idCita;
    private String nombrePaciente;
    private boolean esEmergencia;
    private String motivoVisita;
    private String hallazgosClinicos;
    private String codigoCie10;
    private String diagnostico;
    private String planTratamiento;
    private String notasAdicionales;
    private String estadoConsulta; // "En curso" | "Finalizada" | null (todavía no se ha guardado nada)
}
