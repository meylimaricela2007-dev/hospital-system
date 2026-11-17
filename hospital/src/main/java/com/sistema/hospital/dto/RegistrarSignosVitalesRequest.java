package com.sistema.hospital.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * CU-07, pasos 4-8 del flujo normal. Los rangos son EXACTAMENTE los de
 * [RN-CU07-01] a [RN-CU07-05]; jakarta.validation los revisa antes de que
 * el request llegue al servicio, y GlobalExceptionHandler junta los
 * mensajes de todos los campos que fallen (FA02: "el sistema muestra el
 * mensaje de error específico del campo que falló").
 */
@Data
public class RegistrarSignosVitalesRequest {

    // [RN-CU07-01] Presión arterial: 60-250 / 40-150 mmHg
    @NotNull(message = "La presión sistólica es obligatoria.")
    @Min(value = 60, message = "La presión sistólica debe estar entre 60 y 250 mmHg.")
    @Max(value = 250, message = "La presión sistólica debe estar entre 60 y 250 mmHg.")
    private Integer presionSistolica;

    @NotNull(message = "La presión diastólica es obligatoria.")
    @Min(value = 40, message = "La presión diastólica debe estar entre 40 y 150 mmHg.")
    @Max(value = 150, message = "La presión diastólica debe estar entre 40 y 150 mmHg.")
    private Integer presionDiastolica;

    // [RN-CU07-02] Temperatura: 34-42 °C
    @NotNull(message = "La temperatura es obligatoria.")
    @DecimalMin(value = "34.0", message = "La temperatura debe estar entre 34 y 42 °C.")
    @DecimalMax(value = "42.0", message = "La temperatura debe estar entre 34 y 42 °C.")
    private BigDecimal temperatura;

    // [RN-CU07-03] Peso: 0.5-300 kg
    @NotNull(message = "El peso es obligatorio.")
    @DecimalMin(value = "0.5", message = "El peso debe estar entre 0.5 y 300 kg.")
    @DecimalMax(value = "300", message = "El peso debe estar entre 0.5 y 300 kg.")
    private BigDecimal peso;

    // [RN-CU07-04] Talla: 30-250 cm
    @NotNull(message = "La talla es obligatoria.")
    @DecimalMin(value = "30", message = "La talla debe estar entre 30 y 250 cm.")
    @DecimalMax(value = "250", message = "La talla debe estar entre 30 y 250 cm.")
    private BigDecimal talla;

    // [RN-CU07-05] Frecuencia cardíaca: 30-220 lpm
    @NotNull(message = "La frecuencia cardíaca es obligatoria.")
    @Min(value = 30, message = "La frecuencia cardíaca debe estar entre 30 y 220 lpm.")
    @Max(value = 220, message = "La frecuencia cardíaca debe estar entre 30 y 220 lpm.")
    private Integer frecuenciaCardiaca;

    // FA01: indicador "¿Emergencia?". Opcional; si no viene, se asume false.
    private Boolean esEmergencia;
}
