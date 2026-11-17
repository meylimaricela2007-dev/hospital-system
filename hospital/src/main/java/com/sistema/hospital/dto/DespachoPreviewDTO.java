package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** CU-11, paso 4: detalle de la receta lista para despachar. */
@Data
@AllArgsConstructor
public class DespachoPreviewDTO {
    private Integer idReceta;
    private Integer idConsulta;
    private String paciente;
    private LocalDateTime fechaEmision;
    private long diasTranscurridos;
    private String notas;
    private String sucursal;
    private List<ItemDespachoDTO> items;
    /** FA01: alertas "[Nombre]: Stock bajo — disponible: [X] (mínimo: [Y])." */
    private List<String> alertas;
}
