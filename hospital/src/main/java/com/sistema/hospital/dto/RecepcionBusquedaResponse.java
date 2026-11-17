package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Respuesta única para GET /api/recepcion/buscar, sin importar si se buscó
 * por número de cita o por DPI (FA02). El campo "resultado" le dice al
 * frontend (recepcion.js) qué pantalla/botones mostrar; es más explícito
 * que hacer que el frontend adivine el caso a partir de combinaciones de
 * campos nulos.
 *
 * Valores posibles de "resultado":
 *   CITA_ENCONTRADA          -> flujo normal, paso 4 en adelante
 *   CITA_PENDIENTE_PAGO      -> FA05
 *   CITA_CANCELADA           -> FA06
 *   PACIENTE_SIN_CITAS       -> FA04 (por DPI, sin citas activas hoy)
 *   PACIENTE_NO_REGISTRADO   -> FA03 (por DPI, no existe en el sistema)
 *   NO_ES_PACIENTE           -> el DPI pertenece a personal interno, no a un paciente
 *   SIN_RESULTADOS           -> número de cita que no existe
 */
@Data
@AllArgsConstructor
public class RecepcionBusquedaResponse {
    private String resultado;
    private String mensaje;
    private String subMensaje;             // texto secundario que piden FA03/FA04 (puede ser null)
    private RecepcionCitaDTO cita;         // null salvo en CITA_ENCONTRADA / CITA_PENDIENTE_PAGO / CITA_CANCELADA
    private Integer idPacienteEncontrado;  // para FA04: "Nueva Cita (Walk-in)"
    private String nombrePacienteEncontrado;
}
