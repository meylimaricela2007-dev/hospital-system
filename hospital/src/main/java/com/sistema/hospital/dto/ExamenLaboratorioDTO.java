package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ExamenLaboratorioDTO {
    private Integer idExamen;
    private String nombre;
    private String laboratorio;
    private BigDecimal precio;
}
