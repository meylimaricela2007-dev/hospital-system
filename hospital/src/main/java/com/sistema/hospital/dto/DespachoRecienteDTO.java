package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** CU-11: fila del panel de farmacia ("Despachos recientes" de la sucursal). */
@Data
@AllArgsConstructor
public class DespachoRecienteDTO {
    private Integer idDespacho;
    private Integer idReceta;
    private String paciente;
    private BigDecimal montoTotal;
    private String estado;
    private LocalDateTime fechaDespacho;
}
