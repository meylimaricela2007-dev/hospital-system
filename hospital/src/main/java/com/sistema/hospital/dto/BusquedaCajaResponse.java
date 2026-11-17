package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CU-06, paso 1 / FA02. "resultado" es "CITA_ENCONTRADA" o "SIN_RESULTADOS";
 * a diferencia de CU-05, aquí solo interesan las citas "Pendiente de pago",
 * así que no hace falta un catálogo tan grande de resultados posibles.
 */
@Data
@AllArgsConstructor
public class BusquedaCajaResponse {
    private String resultado;
    private String mensaje;
    private CitaCajaDTO cita;
}
