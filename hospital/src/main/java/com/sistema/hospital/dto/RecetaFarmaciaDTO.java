package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** CU-11, paso 2: una receta en la tabla de búsqueda (ID, Consulta, Fecha de Emisión, Vigencia, Notas). */
@Data
@AllArgsConstructor
public class RecetaFarmaciaDTO {
    private Integer idReceta;
    private Integer idConsulta;
    private String paciente;
    private LocalDateTime fechaEmision;
    private long diasTranscurridos;
    private boolean vigente;
    private String notas;
}
