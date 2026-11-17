package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Portal del paciente: "Mis Citas" (historial + próximas). */
@Data
@AllArgsConstructor
public class MiCitaDTO {
    private Integer idCita;
    private String especialidad;
    private String nombreMedico;
    private String sucursal;
    private LocalDate fechaCita;
    private LocalTime horaCita;
    private String motivoConsulta;
    private String estadoCita;
    private BigDecimal montoTotal;
    private boolean esEmergencia;
}
