package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * CU-07, paso 9 del flujo normal / FA01. "alertasClinicas" es lo que pide
 * el componente VitalSignAlertsDisplay (FA03): lista de valores que están
 * dentro del rango de captura pero fuera del rango clínico normal.
 */
@Data
@AllArgsConstructor
public class RegistrarSignosVitalesResponse {
    private boolean success;
    private String mensaje;
    private List<String> alertasClinicas;
    private boolean esEmergencia;
}
