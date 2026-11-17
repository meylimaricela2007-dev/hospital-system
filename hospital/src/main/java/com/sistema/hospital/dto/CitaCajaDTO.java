package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** CU-06, paso 2: "el sistema muestra el detalle de la cita y el monto a cobrar". */
@Data
@AllArgsConstructor
public class CitaCajaDTO {
    private Integer idCita;
    private String nombrePaciente;
    private String especialidad;
    private String nombreMedico;
    private String sucursal;
    private LocalDate fechaCita;
    private LocalTime horaCita;
    private BigDecimal montoTotal;
}
