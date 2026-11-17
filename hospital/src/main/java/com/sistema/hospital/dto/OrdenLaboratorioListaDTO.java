package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila de la tabla "Órdenes de Laboratorio" (CU-09, paso 1) y del
 * listado de órdenes pendientes en "Cobro de Laboratorio en Caja"
 * (CU-10, paso 3): número de orden, paciente, DPI, cantidad de exámenes,
 * fecha de creación y monto total.
 */
@Data
@AllArgsConstructor
public class OrdenLaboratorioListaDTO {
    private Integer idOrden;
    private String numeroOrden;
    private String nombrePaciente;
    private String dpiPaciente;
    private String nombreMedico;
    private String estado;
    private boolean esExterna;
    private int cantidadExamenes;
    private int examenesPublicados;
    private BigDecimal montoTotal;
    private LocalDateTime fechaCreacion;
}
