package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * CU-08, FA04, paso 6: "Receta médica generada exitosamente. Medicamentos:
 * [lista]. El paciente puede adquirirlos en la farmacia de la clínica."
 * "idReceta" hace de referencia visible para el paciente: esta base de
 * datos no tiene una columna de "número de receta" como sí tiene
 * ordenes_laboratorio con numero_orden (ver el comentario en RecetaMedica.java).
 */
@Data
@AllArgsConstructor
public class RecetaResponse {
    private String mensaje;
    private Integer idReceta;
    private List<String> medicamentos;
}
