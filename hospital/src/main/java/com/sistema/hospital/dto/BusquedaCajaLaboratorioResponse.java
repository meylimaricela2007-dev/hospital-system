package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * CU-10, pasos 2-3 / FA01. resultado = "ORDENES_ENCONTRADAS" | "SIN_RESULTADOS".
 * Por DPI puede haber varias órdenes pendientes; por No. Orden, como mucho una.
 */
@Data
@AllArgsConstructor
public class BusquedaCajaLaboratorioResponse {
    private String resultado;
    private String mensaje;
    private List<OrdenLaboratorioListaDTO> ordenes;
}
