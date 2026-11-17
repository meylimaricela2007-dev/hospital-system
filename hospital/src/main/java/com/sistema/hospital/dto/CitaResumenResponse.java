package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Resumen de una cita para las pantallas de confirmación y pago. */
@Data
@AllArgsConstructor
public class CitaResumenResponse {
    private Integer idCita;
    private String nombrePaciente;
    private String nombreMedico;
    private String nombreEspecialidad;
    private String nombreSucursal;
    private LocalDate fechaCita;
    private LocalTime horaCita;
    private String motivoConsulta;
    private String estadoCita;
    private BigDecimal monto;
    private LocalDateTime fechaExpiracionReserva;
}
