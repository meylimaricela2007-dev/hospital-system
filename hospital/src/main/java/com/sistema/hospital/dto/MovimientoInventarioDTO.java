package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** CU-15: una fila de la bitácora de movimientos de inventario. */
@Data
@AllArgsConstructor
public class MovimientoInventarioDTO {
    private Integer idMovimiento;
    private Integer idTipoMovimiento;
    private String tipo;
    private boolean entrada;
    private Integer idMedicamento;
    private String medicamento;
    private Integer idSucursal;
    private String sucursal;
    private Integer cantidad;
    private Integer stockAnterior;
    private Integer stockNuevo;
    private BigDecimal costoUnitario;
    private String numeroReferencia;
    private String motivoNotas;
    private String usuario;
    private LocalDateTime fechaHora;
    private boolean estado;
}
