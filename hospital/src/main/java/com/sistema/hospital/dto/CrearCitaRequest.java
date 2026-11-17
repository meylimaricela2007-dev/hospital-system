package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * NUEVO (CU-05, FA04 "Nueva Cita (Walk-in)"): la única excepción es
 * idPacienteWalkIn. Cuando el Recepcionista llega a este wizard desde el
 * módulo de Recepción (porque encontró un paciente registrado sin citas
 * activas), la cita se debe crear a nombre de ESE paciente y no de quien
 * tiene la sesión iniciada (el propio Recepcionista). CitaController valida
 * que solo personal interno (no un Paciente) pueda usar este campo.
 */
@Data
public class CrearCitaRequest {

    private Integer idPacienteWalkIn;

    // NUEVO (CU-08, FA02 "Cita de seguimiento"): cuando el médico agenda un
    // seguimiento desde la sección "Evaluados", ambos campos vienen desde
    // agendar.html (banner + hidden inputs, ver citas.js). idConsultaOrigen
    // por sí solo ya marca la cita como esSeguimiento=true;
    // idTipoSeguimiento es opcional (motivo del seguimiento).
    private Integer idConsultaOrigen;
    private Integer idTipoSeguimiento;

    // NUEVO (CU-12, paso 6): prioridad del seguimiento (Baja / Normal / Alta). Opcional; por defecto Normal.
    private Integer idPrioridad;

    @NotNull(message = "Debe seleccionar una sucursal.")
    private Integer sucursalId;

    @NotNull(message = "Debe seleccionar una especialidad.")
    private Integer especialidadId;

    @NotNull(message = "Debe seleccionar un médico.")
    private Integer medicoId;

    @NotNull(message = "Debe seleccionar un horario disponible.")
    private Integer horarioId;

    @NotBlank(message = "El motivo de la consulta es obligatorio.")
    @Size(min = 10, max = 2000, message = "El motivo debe contener entre 10 y 2000 caracteres.")
    private String motivo;
}
