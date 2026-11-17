package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** CU-15, RN-CU13-03: panel informativo en tiempo real (medicamento + sucursal). */
@Data
@AllArgsConstructor
public class InventarioInfoDTO {
    private boolean existeInventario;
    private Integer stockActual;
    private Integer stockMinimo;
}
