package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** Un horario disponible (paso 4 del wizard: calendario + horas). */
@Data
@AllArgsConstructor
public class HorarioDisponibleDTO {
    private Integer idHorario;
    private LocalDate fecha;
    private LocalTime horaInicio;
}
