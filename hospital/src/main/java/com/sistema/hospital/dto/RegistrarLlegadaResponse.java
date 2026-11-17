package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CU-05, paso 8 del flujo normal (y FA01/FA08 para emergencias): mensaje de
 * confirmación tras registrar la llegada, más el indicador que el
 * documento pide mostrar en la tarjeta de la cita: "Llegada registrada —
 * esperando llamado de enfermería".
 */
@Data
@AllArgsConstructor
public class RegistrarLlegadaResponse {
    private boolean success;
    private String mensaje;
    private String indicador;
    private boolean esEmergencia;
    private RecepcionCitaDTO cita;
}
