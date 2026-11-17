package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** CU-11, pasos 4-5: un medicamento recetado con su disponibilidad en el inventario de la sucursal. */
@Data
@AllArgsConstructor
public class ItemDespachoDTO {
    private Integer idDetalleReceta;
    private Integer idMedicamento;
    private String medicamento;
    private String unidad;
    private String dosis;
    private Integer cantidadRecetada;
    private BigDecimal precioUnitario;
    /** null = el medicamento no tiene inventario registrado en esta sucursal. */
    private Integer stockDisponible;
    private Integer stockMinimo;
    private boolean esControlado;
    private boolean sinInventario;
    private boolean stockBajo;
}
