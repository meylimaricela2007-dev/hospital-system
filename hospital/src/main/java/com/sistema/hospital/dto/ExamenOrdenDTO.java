package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CU-09, pasos 3 y 9: un examen dentro de la orden, con su precio y (si ya
 * se registró) su resultado. rangoReferencia y unidadMedida vienen del
 * catálogo de exámenes; unidadResultado es la que escribió el laboratorista.
 */
@Data
@AllArgsConstructor
public class ExamenOrdenDTO {
    private Integer idDetalleOrden;
    private String nombreExamen;
    private BigDecimal monto;
    private String rangoReferencia;
    private String unidadMedida;
    private String valorResultado;
    private String unidadResultado;
    private LocalDateTime fechaResultado;
    private boolean fueraDeRango;
    private String notasResultado;
    private boolean publicado;
}
