package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Respuesta de "Guardar Resultado" y "Publicar resultado" (CU-09, pasos
 * 10-12). Devuelve el examen actualizado para que la pantalla lo repinte
 * sin recargar, y el estado de la orden (que pasa a "Completada" cuando
 * se publica el último examen).
 */
@Data
@AllArgsConstructor
public class ResultadoLaboratorioResponse {
    private String mensaje;
    private ExamenOrdenDTO examen;
    private String estadoOrden;
    private boolean ordenCompletada;
}
