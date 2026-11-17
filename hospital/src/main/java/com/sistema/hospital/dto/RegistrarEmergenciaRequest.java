package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * CU-05, FA01 "Paciente llega por emergencia". El documento dice que el
 * Empleado Interno "toma los datos básicos del paciente (nombre, DPI)".
 * En este sistema el paciente YA debe existir en la base de datos (su
 * nombre no se vuelve a pedir ni a editar aquí); si no existe, FA03 lo
 * manda primero a registrarse (CU-02). Por eso este formulario solo pide
 * el DPI para ubicarlo, más la sede y especialidad donde será atendido.
 */
@Data
public class RegistrarEmergenciaRequest {

    @NotNull(message = "El DPI del paciente es obligatorio.")
    @Pattern(regexp = "\\d{13}", message = "El DPI debe contener exactamente 13 dígitos numéricos.")
    private String dpi;

    @NotNull(message = "Debe seleccionar la sede donde se atenderá al paciente.")
    private Integer idSucursal;

    @NotNull(message = "Debe seleccionar la especialidad que atenderá la emergencia.")
    private Integer idEspecialidad;

    // Opcional: si viene vacío, el servicio usa "Atención de emergencia".
    private String motivoConsulta;
}
