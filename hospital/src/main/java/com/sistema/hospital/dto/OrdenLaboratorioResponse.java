package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CU-08, FA01, paso 6: "Orden de laboratorio generada exitosamente.
 * Número de orden: [Número]. Exámenes: [lista]. El paciente debe dirigirse
 * al área de laboratorio."
 */
@Data
@AllArgsConstructor
public class OrdenLaboratorioResponse {
    private String mensaje;
    private String numeroOrden;
    private List<String> examenes;
    private BigDecimal montoTotal;
}
