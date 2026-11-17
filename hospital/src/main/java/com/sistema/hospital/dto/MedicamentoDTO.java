package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class MedicamentoDTO {
    private Integer idMedicamento;
    private String nombre;
    private String unidad;
    private BigDecimal precio;
}
