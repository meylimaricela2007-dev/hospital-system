package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CU-09, paso 9: formulario de resultado de UN examen.
 * Campos: Valor del Resultado, Unidad, Fecha del Resultado, casilla
 * "Fuera de Rango" (marcada manualmente) y Notas del Resultado.
 */
@Data
public class GuardarResultadoRequest {

    @NotBlank(message = "El valor del resultado es obligatorio.")
    @Size(max = 100, message = "El valor del resultado no puede exceder los 100 caracteres.")
    private String valorResultado;

    @Size(max = 30, message = "La unidad no puede exceder los 30 caracteres.")
    private String unidadResultado;

    @NotNull(message = "La fecha del resultado es obligatoria.")
    private LocalDateTime fechaResultado;

    private Boolean fueraDeRango;

    @Size(max = 2000, message = "Las notas del resultado no pueden exceder los 2000 caracteres.")
    private String notasResultado;
}
